package com.kazuki.replaceobject.replaceobject;

import android.media.Image;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.ImageFormat;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.kazuki.replaceobject.R;
import com.kazuki.replaceobject.helpers.CameraPermissionHelper;
import com.kazuki.replaceobject.helpers.CpuImageDisplayRotationHelper;
import com.kazuki.replaceobject.helpers.FullScreenHelper;
import com.kazuki.replaceobject.helpers.GestureHelper;
import com.kazuki.replaceobject.helpers.SnackbarHelper;
import com.kazuki.replaceobject.helpers.TrackingStateHelper;
import com.kazuki.replaceobject.rendering.CpuImageRenderer;
import com.kazuki.replaceobject.rendering.ObjectRenderer;
import com.kazuki.replaceobject.rendering.PlaneRenderer;

import java.io.IOException;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class ReplaceObjectActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
    private static final String TAG = ReplaceObjectActivity.class.getSimpleName();

    // Session management and renderer
    private GLSurfaceView surfaceView;
    private Session session;
    private boolean installRequested;
    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    private CpuImageDisplayRotationHelper cpuImageDisplayRotationHelper;
    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
    private GestureHelper gestureHelper;
    private final CpuImageRenderer cpuImageRenderer = new CpuImageRenderer();
    private final ObjectRenderer virtualObject = new ObjectRenderer();
    private static final String SEARCHING_PLANE_MESSAGE = "Searching for surfaces...";

    // This lock prevents changing resolution as the frame is being rendered. ARCore requires all
    // CPU images to be released before changing resolution.
    private final Object frameImageInUseLock = new Object();

    // anchor for virtual object
    private final float[] anchorMatrix = new float[16];
    private static final float[] DEFAULT_COLOR = new float[]{0f, 0f, 0f, 0f};
    private final ArrayList<Anchor> anchors = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_replaceobject);
        surfaceView = findViewById(R.id.surfaceview);


        // Set up tap listener
        cpuImageDisplayRotationHelper = new CpuImageDisplayRotationHelper(this);
        gestureHelper = new GestureHelper(/*context=*/ this);
        surfaceView.setOnTouchListener(gestureHelper);

        // set up renderer
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(3);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        surfaceView.setRenderer(this);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        surfaceView.setWillNotDraw(false);

        installRequested = false;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (session == null) {
            Exception exception = null;
            String message = null;
            try {
                switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        return;
                    case INSTALLED:
                        break;
                }

                // ARCore requires camera permissions to operate. If we did not yet obtain runtime
                // permission on Android M and above, now is a good time to ask the user for it.
                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this);
                    return;
                }

                // Create the session.
                session = new Session(/* context= */ this);

            } catch (UnavailableArcoreNotInstalledException
                    | UnavailableUserDeclinedInstallationException e) {
                message = "Please install ARCore";
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                message = "Please update ARCore";
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                message = "Please update this app";
                exception = e;
            } catch (UnavailableDeviceNotCompatibleException e) {
                message = "This device does not support AR";
                exception = e;
            } catch (Exception e) {
                message = "Failed to create AR session";
                exception = e;
            }

            if (message != null) {
                messageSnackbarHelper.showError(this, message);
                Log.e(TAG, "Exception creating session", exception);
                return;
            }
        }

        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            messageSnackbarHelper.showError(this, "Camera not available. Try restarting the app.");
            session = null;
            return;
        }

        surfaceView.onResume();
        cpuImageDisplayRotationHelper.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            cpuImageDisplayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                    .show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES30.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
        try {
            cpuImageRenderer.createOnGlThread(this);
            virtualObject.createOnGlThread(/*context=*/ this, "models/andy.obj", "models/andy.png");
            virtualObject.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

        } catch (IOException e) {
            Log.e(TAG, "Failed to read an asset file", e);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        cpuImageDisplayRotationHelper.onSurfaceChanged(width, height);
        GLES30.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);

        if (session == null) {
            return;
        }

        // Synchronize here to avoid calling Session.update or Session.acquireCameraImage while paused.
        synchronized (frameImageInUseLock) {
            // Notify ARCore session that the view size changed so that the perspective matrix and
            // the video background can be properly adjusted.
            cpuImageDisplayRotationHelper.updateSessionIfNeeded(session);

            try {
                session.setCameraTextureName(cpuImageRenderer.getTextureId());
                final Frame frame = session.update();
                final Camera camera = frame.getCamera();

                // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
                trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

                renderProcessedImageCpuDirectAccess(frame);

                // Handle one tap per frame.
                handleTap(frame, camera);

                // Get projection matrix.
                float[] projmtx = new float[16];
                camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

                // Get camera matrix and draw.
                float[] viewmtx = new float[16];
                camera.getViewMatrix(viewmtx, 0);

                // Compute lighting from average intensity of the image.
                // The first three components are color scaling factors.
                // The last one is the average pixel intensity in gamma space.
                final float[] colorCorrectionRgba = new float[4];
                frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

                // No tracking error at this point. If we detected any plane, then hide the
                // message UI, otherwise show searchingPlane message.
                if (hasTrackingPlane()) {
                    messageSnackbarHelper.hide(this);
                } else {
                    messageSnackbarHelper.showMessage(this, SEARCHING_PLANE_MESSAGE);
                }


                // Visualize anchors created by touch.
                if (anchors.get(0).getTrackingState() != TrackingState.TRACKING) {
                    return;
                }
                anchors.get(0).getPose().toMatrix(anchorMatrix, 0);
                virtualObject.updateModelMatrix(anchorMatrix, gestureHelper.getScaleFactor());
                virtualObject.draw(viewmtx, projmtx, colorCorrectionRgba, DEFAULT_COLOR);
            } catch (Exception t) {
                // Avoid crashing the application due to unhandled exceptions
                Log.e(TAG, "Exception on the OenGL thread", t);
            }
        }
    }

    // Handle only one tap per frame, as taps are usually low frequency compared to frame rate.
    private void handleTap(Frame frame, Camera camera) {
        MotionEvent tap = gestureHelper.poll();
        if (tap != null && camera.getTrackingState() == TrackingState.TRACKING) {
            for (HitResult hit : frame.hitTest(tap)) {
                // Check if any plane was hit, and if it was hit inside the plane polygon
                Trackable trackable = hit.getTrackable();
                // Creates an anchor if a plane or an oriented point was hit.
                if ((trackable instanceof Plane
                        && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())
                        && (PlaneRenderer.calculateDistanceToPlane(hit.getHitPose(), camera.getPose()) > 0))
                        || (trackable instanceof Point
                        && ((Point) trackable).getOrientationMode()
                        == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL)) {
                    // Hits are sorted by depth. Consider only closest hit on a plane or oriented point.
                    // Cap the number of objects created. This avoids overloading both the
                    // rendering system and ARCore.
                    if (anchors.size() >= 1) {
                        anchors.get(0).detach();
                        anchors.remove(0);
                    }

                    // Adding an Anchor tells ARCore that it should track this position in
                    // space. This anchor is created on the Plane to place the 3D model
                    // in the correct position relative both to the world and to the plane.
                    anchors.add(hit.createAnchor());
                    break;
                }
            }
        }
    }

    /**
     * Checks if we detected at least one plane.
     */
    private boolean hasTrackingPlane() {
        for (Plane plane : session.getAllTrackables(Plane.class)) {
            if (plane.getTrackingState() == TrackingState.TRACKING) {
                return true;
            }
        }
        return false;
    }


    private static float calculateDistanceToPlane(Pose planePose, Pose cameraPose) {
        float[] normal = new float[3];
        float cameraX = cameraPose.tx();
        float cameraY = cameraPose.ty();
        float cameraZ = cameraPose.tz();
        // Get transformed Y axis of plane's coordinate system.
        planePose.getTransformedAxis(1, 1.0f, normal, 0);
        // Compute dot product of plane's normal with vector from camera to plane center.
        return (cameraX - planePose.tx()) * normal[0]
                + (cameraY - planePose.ty()) * normal[1]
                + (cameraZ - planePose.tz()) * normal[2];
    }

    /**
     * access a CPU image directly from ARCore
     **/
    private void renderProcessedImageCpuDirectAccess(Frame frame) {
        try (Image image = frame.acquireCameraImage()) {
            if (image.getFormat() != ImageFormat.YUV_420_888) {
                throw new IllegalArgumentException("Expected image in YUV_420_888 format, got format" + image.getFormat());
            }

            cpuImageRenderer.drawWithCpuImage(
                    frame,
                    image.getWidth(),
                    image.getHeight(),
                    image.getPlanes()[0].getBuffer(),
                    cpuImageDisplayRotationHelper.getViewportAspectRatio(),
                    cpuImageDisplayRotationHelper.getCameraToDisplayRotation());
        } catch (NotYetAvailableException e) {
            cpuImageRenderer.drawWithoutCpuImage();
        }
    }
}

