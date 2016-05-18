package com.morristaedt.mirror;

import android.content.Context;
import android.content.Intent;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextClock;
import android.widget.ImageView;
import android.widget.TextView;

import com.morristaedt.mirror.configuration.ConfigurationSettings;
import com.morristaedt.mirror.modules.BirthdayModule;
import com.morristaedt.mirror.modules.CalendarModule;
import com.morristaedt.mirror.modules.ChoresModule;
import com.morristaedt.mirror.modules.DayModule;
import com.morristaedt.mirror.modules.ForecastModule;
import com.morristaedt.mirror.modules.GesturesModule;
import com.morristaedt.mirror.modules.NewsModule;
import com.morristaedt.mirror.modules.XKCDModule;
import com.morristaedt.mirror.modules.YahooFinanceModule;
import com.morristaedt.mirror.receiver.AlarmReceiver;
import com.morristaedt.mirror.requests.YahooStockResponse;
import com.morristaedt.mirror.utils.WeekUtil;
import com.squareup.picasso.Picasso;

import org.opencv.android.*;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MirrorActivity extends ActionBarActivity  implements CvCameraViewListener {

    @NonNull
    private ConfigurationSettings mConfigSettings;

    private OrientationEventListener mOrientationListener;

    private TextView mBirthdayText;
    private TextView mDayText;
    private TextView mDayTimeText;
    private TextClock mClock;
    private TextView mWeatherSummary;
    private TextView mBikeTodayText;
    private TextView mStockText;
    private TextView mGesturesText;
    private View mWaterPlants;
    private View mGroceryList;
    private ImageView mXKCDImage;
    private TextView mNewsHeadline;
    private TextView mCalendarTitleText;
    private TextView mCalendarDetailsText;

    private boolean mVisible = false; // By default we don't show anything

    private CameraBridgeViewBase mCameraView;
    private CascadeClassifier mClassifier;
    private Mat mGrayImage;
    private Mat mRotatedImage;
    private double mAngle;
    private int mAbsFaceSize;
    private Size mResizedSize;

    private static ScheduledExecutorService mExecutor;

    /*
    Init the various listeners needed
     */

    private XKCDModule.XKCDListener mXKCDListener = new XKCDModule.XKCDListener() {
        @Override
        public void onNewXKCDToday(String url) {
            if (TextUtils.isEmpty(url)) {
                mXKCDImage.setVisibility(View.GONE);
            } else {
                Picasso.with(MirrorActivity.this).load(url).into(mXKCDImage);
                mXKCDImage.setVisibility(View.VISIBLE);
            }
        }
    };

    private YahooFinanceModule.StockListener mStockListener = new YahooFinanceModule.StockListener() {
        @Override
        public void onNewStockPrice(YahooStockResponse.YahooQuoteResponse quoteResponse) {
            if (quoteResponse == null) {
                mStockText.setText(null);
            } else {
                mStockText.setText("$" + quoteResponse.symbol + " $" + quoteResponse.LastTradePriceOnly);
            }
        }
    };

    private ForecastModule.ForecastListener mForecastListener = new ForecastModule.ForecastListener() {
        @Override
        public void onWeatherToday(String weatherToday) {
            if (!TextUtils.isEmpty(weatherToday)) {
                mWeatherSummary.setText(weatherToday);
            }
        }

        @Override
        public void onShouldBike(boolean showToday, boolean shouldBike) {
            if (mConfigSettings.showBikingHint()) {
                mBikeTodayText.setText(shouldBike ? R.string.bike_today : R.string.no_bike_today);
            }
        }
    };

    private NewsModule.NewsListener mNewsListener = new NewsModule.NewsListener() {
        @Override
        public void onNewNews(String headline) {
            mNewsHeadline.setText(headline);
        }
    };

    private GesturesModule mGesturesModule = new GesturesModule();

    private GesturesModule.GesturesListener mGesturesListener = new GesturesModule.GesturesListener() {
        @Override
        public void onFaceDetected(final boolean faceDetected) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mVisible != faceDetected) {
                        mVisible = faceDetected;
                        setViewState();
                    }
                }
            });
        }
    };

    private CalendarModule.CalendarListener mCalendarListener = new CalendarModule.CalendarListener() {
        @Override
        public void onCalendarUpdate(String title, String details) {
            mCalendarTitleText.setText(title);
            mCalendarDetailsText.setText(details);
        }
    };

    /*
    Implementation of the lifecycle of the activity
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mConfigSettings = new ConfigurationSettings(this);

        setContentView(R.layout.activity_mirror);
        AlarmReceiver.startMirrorUpdates(this);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_IMMERSIVE;
            decorView.setSystemUiVisibility(uiOptions);
            ActionBar actionBar = getSupportActionBar();
            actionBar.hide();
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mOrientationListener = new OrientationEventListener(getApplicationContext()) {
            @Override
            public void onOrientationChanged(int orientation) {
                switch (orientation) {
                    default:
                    case Surface.ROTATION_0:
                        mAngle = 0.0;
                        break;
                    case Surface.ROTATION_90:
                        mAngle = 270.0;
                        break;
                    case Surface.ROTATION_180:
                        mAngle = 180.0;
                        break;
                    case Surface.ROTATION_270:
                        mAngle = 90.0;
                        break;
                }
            }
        };

        if (mOrientationListener.canDetectOrientation()) {
            mOrientationListener.enable();
        }

        mExecutor = Executors.newScheduledThreadPool(1);
        mExecutor.scheduleAtFixedRate(new Runnable() {
            public void run() {
                Log.v("MirrorActivity",
                        "Updating data as scheduled");
                updateMirrorData();
            }
        }, 0, 1, TimeUnit.HOURS);

        mBirthdayText = (TextView) findViewById(R.id.birthday_text);
        mDayText = (TextView) findViewById(R.id.day_text);
        mDayTimeText = (TextView) findViewById(R.id.daytime_text);
        mClock = (TextClock) findViewById(R.id.digital_clock);
        mWeatherSummary = (TextView) findViewById(R.id.weather_summary);
        mWaterPlants = findViewById(R.id.water_plants);
        mGroceryList = findViewById(R.id.grocery_list);
        mBikeTodayText = (TextView) findViewById(R.id.can_bike);
        mStockText = (TextView) findViewById(R.id.stock_text);
        mGesturesText = (TextView) findViewById(R.id.gestures_text);
        mXKCDImage = (ImageView) findViewById(R.id.xkcd_image);
        mNewsHeadline = (TextView) findViewById(R.id.news_headline);
        mCalendarTitleText = (TextView) findViewById(R.id.calendar_title);
        mCalendarDetailsText = (TextView) findViewById(R.id.calendar_details);
        mCameraView = (CameraBridgeViewBase) findViewById(R.id.mirror_surface_view);
        mCameraView.setCvCameraViewListener(this);
        mCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT);
        mGesturesText.setText("I see you!");

        //Make marquee effect work for long text
        mCalendarTitleText.setSelected(true);
        mCalendarDetailsText.setSelected(true);
        mNewsHeadline.setSelected(true);

        if (mConfigSettings.invertXKCD()) {
            //Negative of XKCD image
            float[] colorMatrixNegative = {
                    -1.0f, 0, 0, 0, 255, //red
                    0, -1.0f, 0, 0, 255, //green
                    0, 0, -1.0f, 0, 255, //blue
                    0, 0, 0, 1.0f, 0 //alpha
            };
            ColorFilter colorFilterNegative = new ColorMatrixColorFilter(colorMatrixNegative);
            mXKCDImage.setColorFilter(colorFilterNegative); // not inverting for now
        }

        mCameraView.setVisibility(View.VISIBLE);

        setViewState();
    }

    @Override
    protected void onPause() {
        super.onPause();

       /* TODO if (mGesturesModule != null) {
            mGesturesModule.release();
        } */
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setViewState();
    }

    @Override
    protected void onResume() {
        super.onResume();

        mGesturesModule.setGesturesListener(mGesturesListener);
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, this, mLoaderCallback);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        AlarmReceiver.stopMirrorUpdates(this);
        Intent intent = new Intent(this, SetUpActivity.class);
        startActivity(intent);
    }

    /*
    Inner functions to display the data
     */

    private void updateMirrorData() {

        String birthday = BirthdayModule.getBirthday();
        if (!TextUtils.isEmpty(birthday)) {
            mBirthdayText.setText(getString(R.string.happy_birthday, birthday));
        } else {
            mBirthdayText.setText("");
        }

        if (mConfigSettings.showNewsHeadline()) {
            NewsModule.getNewsHeadline(mNewsListener);
        }

        if (mConfigSettings.showXKCD()) {
            XKCDModule.getXKCDForToday(mXKCDListener);
        }

        if (mConfigSettings.showNextCalendarEvent()) {
            CalendarModule.getCalendarEvents(this, mCalendarListener);
        }

        if (mConfigSettings.showStock() && (ConfigurationSettings.isDemoMode() || WeekUtil.isWeekdayAfterFive())) {
            YahooFinanceModule.getStockForToday(mConfigSettings.getStockTickerSymbol(), mStockListener);
        }

        // Get the API key for whichever weather service API key is available
        // These should be declared as a string in xml
        int forecastApiKeyRes = getResources().getIdentifier("dark_sky_api_key", "string", getPackageName());
        int openWeatherApiKeyRes = getResources().getIdentifier("open_weather_api_key", "string", getPackageName());

        if (forecastApiKeyRes != 0) {
            ForecastModule.getForecastIOHourlyForecast(getString(forecastApiKeyRes), mConfigSettings.getForecastUnits(), mConfigSettings.getLatitude(), mConfigSettings.getLongitude(), mForecastListener);
        } else if (openWeatherApiKeyRes != 0) {
            ForecastModule.getOpenWeatherForecast(getString(openWeatherApiKeyRes), mConfigSettings.getForecastUnits(), mConfigSettings.getLatitude(), mConfigSettings.getLongitude(), mForecastListener);
        }
    }
    private void setViewState() {

        mDayText.setText(DayModule.getDay());
        mDayTimeText.setText(DayModule.getTimeOfDayWelcome(getResources()));

        mBirthdayText.setVisibility(!TextUtils.isEmpty(mBirthdayText.getText()) && mVisible ? View.VISIBLE : View.GONE);
        mDayText.setVisibility(!TextUtils.isEmpty(mDayText.getText()) && mVisible ? View.VISIBLE : View.GONE);
        mDayTimeText.setVisibility(!TextUtils.isEmpty(mDayTimeText.getText()) && mVisible ? View.VISIBLE : View.GONE);
        mGesturesText.setVisibility(!TextUtils.isEmpty(mGesturesText.getText()) && mVisible ? View.VISIBLE : View.GONE);
        mWeatherSummary.setVisibility(!TextUtils.isEmpty(mWeatherSummary.getText()) && mVisible ? View.VISIBLE : View.GONE);
        mClock.setVisibility(!TextUtils.isEmpty(mClock.getText()) && mVisible ? View.VISIBLE : View.GONE);
        mNewsHeadline.setVisibility(!TextUtils.isEmpty(mNewsHeadline.getText()) && mVisible ? View.VISIBLE : View.GONE);
        mCalendarTitleText.setVisibility(!TextUtils.isEmpty(mCalendarTitleText.getText()) && mVisible ? View.VISIBLE : View.GONE);
        mCalendarDetailsText.setVisibility(!TextUtils.isEmpty(mCalendarDetailsText.getText()) && mVisible ? View.VISIBLE : View.GONE);
        mStockText.setVisibility(!TextUtils.isEmpty(mStockText.getText()) && mVisible ? View.VISIBLE : View.GONE);

        mWaterPlants.setVisibility(ChoresModule.waterPlantsToday() && mVisible ? View.VISIBLE : View.GONE);
        mGroceryList.setVisibility(ChoresModule.makeGroceryListToday() && mVisible ? View.VISIBLE : View.GONE);

        if (mVisible) { // TODO: discriminate if there's an image displayed
            mXKCDImage.setVisibility(View.VISIBLE);
        } else {
            mXKCDImage.setVisibility(View.GONE);
        }
    }

    /*
    Implements OpenCV CameraListener
     */

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status){
                case LoaderCallbackInterface.SUCCESS:
                    initOpenCV();
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };

    protected void initOpenCV() {
        try{
            // Copy the resource into a temp file so OpenCV can load it
            InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
            File cascadeDir = getDir("c", Context.MODE_PRIVATE);
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
                Log.v("CVActivity","Error loading classifier. It seems empty.");
                mClassifier = null;
                return;
            }
            else
            {
                Log.v("CVActivity", "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());
            }
            mResizedSize = new Size(1.0, 1.0);
        } catch (Exception e) {
            Log.e("CVActivity", "Error loading cascade", e);
        }

        // And we are ready to go
        mCameraView.enableView();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mGrayImage = new Mat(height, width, CvType.CV_8UC4);
        mRotatedImage = new Mat(height, width, CvType.CV_8UC4);

        mAbsFaceSize = (int) (height * 0.1);
    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(Mat aInputFrame) {
        // Create a rotated grayscale image
        Point center = new Point( aInputFrame.cols()/2, aInputFrame.rows()/2 );
        Mat rotationMatrix = Imgproc.getRotationMatrix2D(center, mAngle, 1.0);
        Imgproc.warpAffine(aInputFrame, mRotatedImage, rotationMatrix, aInputFrame.size());
        Imgproc.cvtColor(mRotatedImage, mGrayImage, Imgproc.COLOR_RGBA2RGB);

        MatOfRect faces = new MatOfRect();

        // Use the classifier to detect faces
        if (mClassifier != null) {
            mClassifier.detectMultiScale(mGrayImage, faces, 1.1, 2, 2,
                    new Size(mAbsFaceSize, mAbsFaceSize), new Size());
        }

        Rect[] facesArray = faces.toArray();

        // Pass the detected faces (if any), to the module for processing
        mGesturesModule.receiveFrames(facesArray);

        return mRotatedImage;
    }
}
