package com.morristaedt.mirror;

import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v7.app.ActionBarActivity;
import android.view.Surface;
import android.view.WindowManager;
import android.util.Log;
import android.content.Context;

import org.opencv.android.*;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class CVActivity extends ActionBarActivity implements CvCameraViewListener {

    private CameraBridgeViewBase mCameraView;
    private CascadeClassifier mClassifier;
    private Mat mGrayImage;
    private Mat mRotatedImage;
    private int mAbsFaceSize;
    private Size mResizedSize;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status){
                case LoaderCallbackInterface.SUCCESS:
                    initializeOpenCVDependencies();
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };

    protected void initializeOpenCVDependencies() {
        try{
            // Copy the resource into a temp file so OpenCV can load it
            InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
            File cascadeDir = getDir("c", Context.MODE_WORLD_READABLE);
            File mCascadeFile = new File(cascadeDir, "lbpcascade.xml");
            FileOutputStream os = new FileOutputStream(mCascadeFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();

            // Load the cascade classifier
            mClassifier = new CascadeClassifier(mCascadeFile.getAbsolutePath());
            mClassifier.load(mCascadeFile.getAbsolutePath());

            if(mClassifier.empty())
            {
                Log.v("CVActivity","--(!)Error loading A\n");
                mClassifier = null;
                return;
            }
            else
            {
                Log.v("CVActivity",
                        "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());
            }
            mResizedSize = new Size(1.0, 1.0);
        } catch (Exception e) {
            Log.e("CVActivity", "Error loading cascade", e);
        }

        // And we are ready to go
        mCameraView.enableView();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // now we create the camera view and assign it to this activity
        mCameraView = new JavaCameraView(this, CameraBridgeViewBase.CAMERA_ID_FRONT);
        mCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);
        setContentView(mCameraView);
        mCameraView.setCvCameraViewListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, this, mLoaderCallback);
    }

    /*
     *
     * Implementation of the  CvCameraViewListener interface
     *
      */

    @Override
    public void onCameraViewStarted(int width, int height) {
        mGrayImage = new Mat(height, width, CvType.CV_8UC4);
        mRotatedImage = new Mat(height, width, CvType.CV_8UC4);
        // The faces will be a 20% of the height of the screen
        mAbsFaceSize = (int) (height * 0.2);
    }
    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(Mat aInputFrame) {
        // Create a rotated grayscale image
        Point center = new Point( aInputFrame.cols()/2, aInputFrame.rows()/2 );

        double angle = 0.0;
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            default:
            case Surface.ROTATION_0:
                angle = 0.0;
                break;
            case Surface.ROTATION_90:
                angle = 270.0;
                break;
            case Surface.ROTATION_180:
                angle = 180.0;
                break;
            case Surface.ROTATION_270:
                angle = 90.0;
                break;
        }
        Mat rotationMatrix = Imgproc.getRotationMatrix2D(center, angle, 1.0);

        Imgproc.warpAffine(aInputFrame, mRotatedImage, rotationMatrix, aInputFrame.size());
        Imgproc.cvtColor(mRotatedImage, mGrayImage, Imgproc.COLOR_RGBA2RGB);

        MatOfRect faces = new MatOfRect();

        // Use the classifier to detect faces
        if (mClassifier != null) {
            mClassifier.detectMultiScale(mGrayImage, faces, 1.1, 2, 2,
                    new Size(mAbsFaceSize, mAbsFaceSize), new Size());
        }

        // If there are any faces found, draw a rectangle around it
        Rect[] facesArray = faces.toArray();
        for (int i = 0; i <facesArray.length; i++) {
            // process the face in order to feed it to the recognizer
            Mat face = mRotatedImage.submat(facesArray[i]);
            Mat resize_face = new Mat();
            Imgproc.resize(face, resize_face, mResizedSize, 1.0, 1.0, Imgproc.INTER_CUBIC);

            // print a rectangle around the face
            Imgproc.rectangle(mRotatedImage, facesArray[i].tl(), facesArray[i].br(), new Scalar(9, 0, 255, 255), 3);
        }

        return mRotatedImage;
    }

}
