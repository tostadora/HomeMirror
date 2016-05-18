package com.morristaedt.mirror.modules;

import android.content.Context;
import android.util.Log;

import org.opencv.core.Rect;

public class GesturesModule  {

    private static final String TAG = "GesturesModule";
    private Context mContext;

    private GesturesListener mCallBacks;

    public interface GesturesListener {
        void onFaceDetected(boolean faceDetected);
    }

    public void setGesturesListener (GesturesListener listener) {
        this.mCallBacks = listener;
    }

    public void receiveFrames (Rect[] facesArray) {
        if (facesArray.length > 0) {
            Log.v("GesturesModule",
                    "Face Detected: " + facesArray.length);
            mCallBacks.onFaceDetected(true);
        }
        else {
            mCallBacks.onFaceDetected(false);
        }
    }
}
