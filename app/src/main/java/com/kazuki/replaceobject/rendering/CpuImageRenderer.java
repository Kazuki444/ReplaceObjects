package com.kazuki.replaceobject.rendering;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;

import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class CpuImageRenderer {
    private static final String TAG = CpuImageRenderer.class.getSimpleName();

    private static final int COORDS_PER_VERTEX = 2;
    private static final int TEXCOORDS_PER_VERTEX = 2;
    private static final int FLOAT_SIZE = 4;

    private FloatBuffer quadCoords;
    private FloatBuffer quadImgCoords;

    private int quadProgram;

    private int quadPositionAttrib;
    private int quadImgCoordAttrib;
    private int backgroundTextureId = -1;
    private int overlayTextureId = -1;

    public int getTextureId() {
        return backgroundTextureId;
    }

    /**
     * Allocates and initializes OpenGL resources needed by the background renderer. Must be called on
     * the OpenGL thread, typically in {@link GLSurfaceView.Renderer#onSurfaceCreated(GL10,
     * EGLConfig)}.
     *
     * @param context Needed to access shader source.
     */
    public void createOnGlThread(Context context) throws IOException {
        int[] textures = new int[2];
        GLES30.glGenTextures(2, textures, 0);

        // Generate the background texture.
        backgroundTextureId = textures[0];
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, backgroundTextureId);
        GLES30.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST);
        GLES30.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST);

        // Generate the CPU Image overlay texture.
        overlayTextureId = textures[1];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, overlayTextureId);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST);

        int numVertices = QUAD_COORDS.length / COORDS_PER_VERTEX;
        ByteBuffer bbCoords = ByteBuffer.allocateDirect(QUAD_COORDS.length * FLOAT_SIZE);
        bbCoords.order(ByteOrder.nativeOrder());
        quadCoords = bbCoords.asFloatBuffer();
        quadCoords.put(QUAD_COORDS);
        quadCoords.position(0);

        ByteBuffer bbImgCoords =
                ByteBuffer.allocateDirect(numVertices * TEXCOORDS_PER_VERTEX * FLOAT_SIZE);
        bbImgCoords.order(ByteOrder.nativeOrder());
        quadImgCoords = bbImgCoords.asFloatBuffer();

        int vertexShader =
                ShaderUtil.loadGLShader(
                        TAG, context, GLES30.GL_VERTEX_SHADER, "shaders/cpu_screenquad.vert");
        int fragmentShader =
                ShaderUtil.loadGLShader(
                        TAG, context, GLES30.GL_FRAGMENT_SHADER, "shaders/cpu_screenquad.frag");

        quadProgram = GLES30.glCreateProgram();
        GLES30.glAttachShader(quadProgram, vertexShader);
        GLES30.glAttachShader(quadProgram, fragmentShader);
        GLES30.glLinkProgram(quadProgram);
        GLES30.glUseProgram(quadProgram);

        ShaderUtil.checkGLError(TAG, "Program creation");

        quadPositionAttrib = GLES30.glGetAttribLocation(quadProgram, "a_Position");
        quadImgCoordAttrib = GLES30.glGetAttribLocation(quadProgram, "a_ImgCoord");

        int texLoc = GLES30.glGetUniformLocation(quadProgram, "TexCpuImage");
        GLES30.glUniform1i(texLoc, 1);

        ShaderUtil.checkGLError(TAG, "Program parameters");
    }


    /**
     * Draws the AR background image. The image will be drawn such that virtual content rendered with
     * the matrices provided by {@link Frame#getViewMatrix(float[], int)} and {@link
     * Session#getProjectionMatrix(float[], int, float, float)} will accurately follow static physical
     * objects. This must be called <b>before</b> drawing virtual content.
     *
     * @param frame                        The last {@code Frame} returned by {@link Session#update()}.
     * @param imageWidth                   The processed image width.
     * @param imageHeight                  The processed image height.
     * @param processedImageBytesGrayscale the processed bytes of the image, grayscale par only. Can
     *                                     be null.
     * @param screenAspectRatio            The aspect ratio of the screen.
     * @param cameraToDisplayRotation      The rotation of camera with respect to the display. The value is
     *                                     one of android.view.Surface.ROTATION_#(0, 90, 180, 270).
     */
    public void drawWithCpuImage(
            Frame frame,
            int imageWidth,
            int imageHeight,
            Bitmap rgbFrameBitmap,
            float screenAspectRatio,
            int cameraToDisplayRotation) {

        // Apply overlay image buffer
        if (rgbFrameBitmap != null) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, overlayTextureId);
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D,0,rgbFrameBitmap,0);
        }

        if (frame == null) {
            return;
        }

        // Update GPU image texture coordinates.
        frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                quadCoords,
                Coordinates2d.IMAGE_NORMALIZED,
                quadImgCoords);

        // Rest of the draw code is shared between the two functions.
        drawWithoutCpuImage();
    }

    /**
     * Same as above, but will not update the CPU image drawn. Should be used when a CPU image is
     * unavailable for any reason, and only background should be drawn.
     */
    public void drawWithoutCpuImage() {
        // No need to test or write depth, the screen quad has arbitrary depth, and is expected
        // to be drawn first.
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthMask(false);

        GLES30.glUseProgram(quadProgram);

        // Set the vertex positions.
        GLES30.glVertexAttribPointer(
                quadPositionAttrib, COORDS_PER_VERTEX, GLES30.GL_FLOAT, false, 0, quadCoords);

        // Set the CPU image texture coordinates.
        GLES30.glVertexAttribPointer(
                quadImgCoordAttrib, TEXCOORDS_PER_VERTEX, GLES30.GL_FLOAT, false, 0, quadImgCoords);

        // Enable vertex arrays
        GLES30.glEnableVertexAttribArray(quadPositionAttrib);
        GLES30.glEnableVertexAttribArray(quadImgCoordAttrib);

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);

        // Disable vertex arrays
        GLES30.glDisableVertexAttribArray(quadPositionAttrib);
        GLES30.glDisableVertexAttribArray(quadImgCoordAttrib);

        // Restore the depth state for further drawing.
        GLES30.glDepthMask(true);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);

        ShaderUtil.checkGLError(TAG, "Draw");
    }


    private static final float[] QUAD_COORDS =
            new float[]{
                    -1.0f, -1.0f, -1.0f, +1.0f, +1.0f, -1.0f, +1.0f, +1.0f,
            };
}