package com.mapbox.mapboxandroiddemo.examples.labs;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.mapbox.mapboxandroiddemo.R;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.style.layers.FillExtrusionLayer;

import static com.mapbox.mapboxsdk.style.expressions.Expression.exponential;
import static com.mapbox.mapboxsdk.style.expressions.Expression.get;
import static com.mapbox.mapboxsdk.style.expressions.Expression.interpolate;
import static com.mapbox.mapboxsdk.style.expressions.Expression.literal;
import static com.mapbox.mapboxsdk.style.expressions.Expression.stop;
import static com.mapbox.mapboxsdk.style.expressions.Expression.zoom;
import static com.mapbox.mapboxsdk.style.layers.Property.VISIBLE;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.fillExtrusionColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.fillExtrusionHeight;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.fillExtrusionOpacity;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.visibility;

public class AmbientNoiseExtrusionHeightActivity extends AppCompatActivity implements OnMapReadyCallback {

  private static final String LAYER_ID = "mapbox-android-plugin-3d-buildings";
  private MapView mapView;
  private MapboxMap mapboxMap;
  private FillExtrusionLayer fillExtrusionLayer;
  private int color = Color.LTGRAY;
  private float opacity = 0.6f;
  private float minZoomLevel = 15.0f;
  private int bins = 16;
  private int maxHeight = 200;
  private int binWidth = maxHeight / bins;
  private MediaRecorder mRecorder;
  private Thread runner;
  private static double mEMA = 0.0;
  private String TAG = "AmbientNoiseActivity";
  static final private double EMA_FILTER = 0.6;
  static final private int REQUEST_MICROPHONE = 34;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Mapbox access token is configured here. This needs to be called either in your application
    // object or in the same activity which contains the mapview.
    Mapbox.getInstance(this, getString(R.string.access_token));

    // This contains the MapView in XML and needs to be called after the access token is configured.
    setContentView(R.layout.activity_lab_ambient_noise_extrusion_height);

    mapView = findViewById(R.id.mapView);
    mapView.onCreate(savedInstanceState);
    mapView.getMapAsync(this);
  }

  @Override
  public void onMapReady(MapboxMap mapboxMap) {
    this.mapboxMap = mapboxMap;
    fillExtrusionLayer = new FillExtrusionLayer(LAYER_ID, "composite");
    fillExtrusionLayer.setSourceLayer("building");
    fillExtrusionLayer.setMinZoom(minZoomLevel);
    fillExtrusionLayer.setProperties(
      visibility(VISIBLE),
      fillExtrusionColor(color),
      fillExtrusionHeight(
        interpolate(
          exponential(1f),
          zoom(),
          stop(15, literal(0)),
          stop(16, get("height"))
        )
      ),
      fillExtrusionOpacity(opacity)
    );
    mapboxMap.addLayer(fillExtrusionLayer);


    if (runner == null) {
      runner = new Thread() {
        public void run() {
          while (runner != null) {
            try {
              Thread.sleep(1000);
              Log.d(TAG, "Tock");
            } catch (InterruptedException e) {
            }
            mHandler.post(updater);
          }
        }
      };
      runner.start();
      Log.d("Noise", "start runner()");
    }
  }

  // Add the mapView lifecycle to the activity's lifecycle methods
  @Override
  public void onResume() {
    super.onResume();
    mapView.onResume();

    if (ContextCompat.checkSelfPermission(this,
      Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

      ActivityCompat.requestPermissions(this,
        new String[] {Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE);

    } else {
      startRecorder();

    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    Log.d(TAG, "onActivityResult: requestCode = " + requestCode);
    Log.d(TAG, "onActivityResult: resultCode = " + resultCode);
  }

  @Override
  protected void onStart() {
    super.onStart();
    mapView.onStart();
  }

  @Override
  protected void onStop() {
    super.onStop();
    mapView.onStop();
    stopRecorder();
  }

  @Override
  public void onPause() {
    super.onPause();
    mapView.onPause();
  }

  @Override
  public void onLowMemory() {
    super.onLowMemory();
    mapView.onLowMemory();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    mapView.onDestroy();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    mapView.onSaveInstanceState(outState);
  }

  final Runnable updater = new Runnable() {
    public void run() {
      Log.d(TAG, "run: dB = " + Double.toString((getAmplitudeEMA())) + " dB");
    }
  };

  final Handler mHandler = new Handler();

  public void startRecorder() {
    if (mRecorder == null) {
      mRecorder = new MediaRecorder();
      mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
      mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
      mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
      mRecorder.setOutputFile("/dev/null");
      try {
        mRecorder.prepare();
      } catch (java.io.IOException ioe) {
        Log.d(TAG, "IOException: " +
          android.util.Log.getStackTraceString(ioe));

      } catch (java.lang.SecurityException e) {
        Log.d(TAG, "SecurityException: " +
          android.util.Log.getStackTraceString(e));
      }
      try {
        mRecorder.start();
      } catch (java.lang.SecurityException e) {
        Log.d(TAG, "SecurityException: " +
          android.util.Log.getStackTraceString(e));
      }

      //mEMA = 0.0;
    }
  }

  public void stopRecorder() {
    if (mRecorder != null) {
      mRecorder.stop();
      mRecorder.release();
      mRecorder = null;
    }
  }

  public double soundDb(double ampl) {
    return 20 * Math.log10(getAmplitudeEMA() / ampl);
  }

  public double getAmplitude() {
    if (mRecorder != null) {
      return (mRecorder.getMaxAmplitude());
    } else {
      return 0;
    }

  }

  public double getAmplitudeEMA() {
    double amp = getAmplitude();
    mEMA = EMA_FILTER * amp + (1.0 - EMA_FILTER) * mEMA;
    return mEMA;
  }

}
// #-end-code-snippet: simple-map-view-activity full-java