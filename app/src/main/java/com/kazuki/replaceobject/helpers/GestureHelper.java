package com.kazuki.replaceobject.helpers;

import android.content.Context;
import android.graphics.Point;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static android.content.Context.WINDOW_SERVICE;

/**
 * tap     | pop up object
 * scroll  | rotation object
 * pinch   | scaling object
 */
public class GestureHelper implements OnTouchListener {

    private final GestureDetector tapDetector;
    private final BlockingQueue<MotionEvent> queuedSingleTaps = new ArrayBlockingQueue<>(16);

    private final ScaleGestureDetector pinchDetector;
    private float scaleFactor = 1.0f;
    private static float angle=0.0f;
    private final float TOUCH_SCALE_FACTOR = 0.2f;

    public GestureHelper(Context context) {
        tapDetector =
                new GestureDetector(
                        context,
                        new GestureDetector.SimpleOnGestureListener() {
                            @Override
                            public boolean onSingleTapUp(MotionEvent e) {
                                queuedSingleTaps.offer(e);
                                return true;
                            }

                            @Override
                            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                                angle-=distanceX*TOUCH_SCALE_FACTOR;
                                return true;
                            }

                            @Override
                            public boolean onDown(MotionEvent e) {
                                return true;
                            }
                        });

        pinchDetector =
                new ScaleGestureDetector(
                        context,
                        new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            @Override
                            public boolean onScale(ScaleGestureDetector detector) {
                                scaleFactor *= pinchDetector.getScaleFactor();
                                return true;
                            }
                        });
    }

    public MotionEvent poll() {
        return queuedSingleTaps.poll();
    }

    public static float getAngle() {
        return angle;
    }

    public float getScaleFactor() {
        return scaleFactor;
    }


    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        return motionEvent.getPointerCount() == 1
                ? tapDetector.onTouchEvent(motionEvent)
                : pinchDetector.onTouchEvent(motionEvent);
    }
}
