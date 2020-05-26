package com.kazuki.replaceobject.detection;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.Image;
import android.util.Log;

import com.kazuki.replaceobject.inpainting.Inpainting;
import com.kazuki.replaceobject.replaceobject.ObjectList;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

public class ObjectDetector {
    private static final String TAG = ObjectDetector.class.getSimpleName();

    private int[] rgbBytes = null;
    private byte[][] yuvBytes = new byte[3][];
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;

    private final int INAGE_ROTATION = 90; //only Portrait
    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private static final Logger LOGGER = new Logger();

    public Bitmap processImage(Image image, int inputSize, Classifier detector, float confidence, ObjectList objectList) {
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        int cropSize = inputSize;
        rgbBytes = new int[imageWidth * imageHeight];
        rgbFrameBitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);
        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        imageWidth, imageHeight,
                        cropSize, cropSize,
                        INAGE_ROTATION, false);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        // change image format from YUV to RGB
        final Image.Plane[] planes = image.getPlanes();
        fillBytes(planes, yuvBytes);
        ImageUtils.convertYUV420ToARGB8888(
                yuvBytes[0],
                yuvBytes[1],
                yuvBytes[2],
                imageWidth,
                imageHeight,
                planes[0].getRowStride(),
                planes[1].getRowStride(),
                planes[1].getPixelStride(),
                rgbBytes);

        // TF input
        rgbFrameBitmap.setPixels(rgbBytes, 0, imageWidth, 0, 0, imageWidth, imageHeight);
        Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);

        // object detection
        final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);


        // post process
        float minimumConfidence = confidence;
        final List<Classifier.Recognition> mappedRecognitions = new LinkedList<Classifier.Recognition>();

        for (final Classifier.Recognition result : results) {
            final RectF location = result.getLocation();
            if (location != null && result.getConfidence() >= minimumConfidence) {
                cropToFrameTransform.mapRect(location);
                result.setLocation(location);
                mappedRecognitions.add(result);

                // detect replace object name
                objectList.setObjName(result.getTitle());
            }
        }

        // inpainting
        Inpainting inpainting=new Inpainting();
        inpainting.inpaint(rgbFrameBitmap,mappedRecognitions);

        return rgbFrameBitmap;
    }

    protected void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity());
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }
}
