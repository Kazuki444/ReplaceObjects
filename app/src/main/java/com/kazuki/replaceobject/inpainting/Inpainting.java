package com.kazuki.replaceobject.inpainting;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.Log;

import com.kazuki.replaceobject.detection.Classifier;
import com.kazuki.replaceobject.detection.ImageUtils;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;

import java.util.List;

import static org.opencv.core.CvType.CV_8UC1;
import static org.opencv.core.CvType.CV_8UC3;

public class Inpainting {

    public Bitmap inpaint(Bitmap bitmap, List<Classifier.Recognition> results){
        // change format to use OpenCV
        Mat mat = new Mat();
        Mat src=new Mat();
        Utils.bitmapToMat(bitmap,mat,false);
        Imgproc.cvtColor(mat,src,Imgproc.COLOR_RGBA2RGB);
        mat.release();

        /**Inpaint the area of maximum recognition result**/
        // make mask
        Mat mask=new Mat(bitmap.getHeight(),bitmap.getWidth(),CV_8UC1, Scalar.all(0));
        for (Classifier.Recognition result:results){
            if (result.getLocation() == null) {
                continue;
            }
            Point point1=new Point((int)result.getLocation().left,(int)result.getLocation().top);
            Point point2=new Point((int)result.getLocation().right,(int)result.getLocation().bottom);
            Scalar scalar=new Scalar(255);
            Imgproc.rectangle(mask, point1, point2,scalar,-1);
        }

        // Inpainting
        Mat dst=new Mat(bitmap.getHeight(),bitmap.getWidth(),CV_8UC3);
        Photo.inpaint(src,mask,dst,3,Photo.INPAINT_NS);

        src.release();
        mask.release();

        // Change format for OpenGL ES
        Utils.matToBitmap(dst,bitmap);

        dst.release();

        return bitmap;
    }
}
