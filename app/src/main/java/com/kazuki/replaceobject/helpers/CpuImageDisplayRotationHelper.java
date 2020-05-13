package com.kazuki.replaceobject.helpers;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import com.google.ar.core.Session;

public class CpuImageDisplayRotationHelper implements DisplayManager.DisplayListener {
    private boolean viewportChanged;
    private int viewportWidth;
    private int viewportHeight;
    private final Context context;
    private final Display display;

    public CpuImageDisplayRotationHelper(Context context){
        this.context=context;
        display=context.getSystemService(WindowManager.class).getDefaultDisplay();
    }

    public void onResume() {
        context.getSystemService(DisplayManager.class).registerDisplayListener(this, null);
    }

    public void onPause() {
        context.getSystemService(DisplayManager.class).unregisterDisplayListener(this);
    }

    public void onSurfaceChanged(int width, int height) {
        viewportWidth = width;
        viewportHeight = height;
        viewportChanged = true;
    }

    public void updateSessionIfNeeded(Session session) {
        if (viewportChanged) {
            int displayRotation = display.getRotation();
            session.setDisplayGeometry(displayRotation, viewportWidth, viewportHeight);
            viewportChanged = false;
        }
    }

    public int getRotation() {
        return display.getRotation();
    }

    /** Return the aspect ratio of view port **/
    public float getViewportAspectRatio() {
        float aspectRatio;
        switch (getCameraToDisplayRotation()) {
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                aspectRatio = (float) viewportHeight / (float) viewportWidth;
                break;
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
            default:
                aspectRatio = (float) viewportWidth / (float) viewportHeight;
                break;
        }
        return aspectRatio;
    }

    /**
     * Returns the rotation of the back-facing camera with respect to the display. The value is one of
     * android.view.Surface.ROTATION_#(0, 90, 180, 270).
     */
    public int getCameraToDisplayRotation() {
        // Get screen to device rotation in degress.
        int screenDegrees = 0;
        switch (getRotation()) {
            case Surface.ROTATION_0:
                screenDegrees = 0;
                break;
            case Surface.ROTATION_90:
                screenDegrees = 90;
                break;
            case Surface.ROTATION_180:
                screenDegrees = 180;
                break;
            case Surface.ROTATION_270:
                screenDegrees = 270;
                break;
            default:
                break;
        }

        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, cameraInfo);

        int cameraToScreenDegrees = (cameraInfo.orientation - screenDegrees + 360) % 360;

        // Convert degrees to rotation ids.
        int cameraToScreenRotation = Surface.ROTATION_0;
        switch (cameraToScreenDegrees) {
            case 0:
                cameraToScreenRotation = Surface.ROTATION_0;
                break;
            case 90:
                cameraToScreenRotation = Surface.ROTATION_90;
                break;
            case 180:
                cameraToScreenRotation = Surface.ROTATION_180;
                break;
            case 270:
                cameraToScreenRotation = Surface.ROTATION_270;
                break;
            default:
                break;
        }

        return cameraToScreenRotation;
    }

    @Override
    public void onDisplayAdded(int i) {

    }

    @Override
    public void onDisplayRemoved(int i) {

    }

    @Override
    public void onDisplayChanged(int i) {
        viewportChanged=true;
    }
}
