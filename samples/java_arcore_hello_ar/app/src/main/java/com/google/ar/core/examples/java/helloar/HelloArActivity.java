/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ar.core.examples.java.helloar;

import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Frame.TrackingState;
import com.google.ar.core.HitResult;
import com.google.ar.core.PlaneHitResult;
import com.google.ar.core.Session;
import com.google.ar.core.examples.java.helloar.rendering.Scene;

import java.util.concurrent.ArrayBlockingQueue;

/**
 * This is a simple example that shows how to create an augmented reality (AR) application using
 * the ARCore API. The application will display any detected planes and will allow the user to
 * tap on a plane to place a 3d model of the Android robot.
 */
public class HelloArActivity extends AppCompatActivity {
  private static final String TAG = HelloArActivity.class.getSimpleName();

  // Rendering. The Renderers are created here, and initialized when the GL surface is created.
  private GLSurfaceView mSurfaceView;
  private Scene scene;
  private Config mDefaultConfig;
  private Session mSession;
  private Snackbar mLoadingMessageSnackbar = null;

  // Tap handling and UI.
  private ArrayBlockingQueue<MotionEvent> mQueuedSingleTaps = new ArrayBlockingQueue<>(16);

  private final View.OnTouchListener tapListener = new View.OnTouchListener() {
    @Override
    public boolean onTouch(View v, MotionEvent event) {
      if (event.getAction() == MotionEvent.ACTION_UP) {
        // Queue tap if there is space. Tap is lost if queue is full.
        mQueuedSingleTaps.offer(event);
      }
      return true;
    }
  };

  private final Scene.DrawingCallback drawCallback = new Scene.DrawingCallback() {
    @Override public void onDraw(Frame frame) {
      handleTap(frame);
    }

    @Override public void trackingPlane() {
      hideLoadingMessage();
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    mSurfaceView = findViewById(R.id.surfaceview);

    mSession = new Session(this);
    scene = new Scene(this, mSurfaceView, mSession, drawCallback);

    // Create default config, check is supported, create session from that config.
    mDefaultConfig = Config.createDefaultConfig();
    if (!mSession.isSupported(mDefaultConfig)) {
      Toast.makeText(this, "This device does not support AR", Toast.LENGTH_LONG).show();
      finish();
      return;
    }

    // Set up tap listener.
    mSurfaceView.setOnTouchListener(tapListener);
  }

  @Override
  protected void onResume() {
    super.onResume();

    // ARCore requires camera permissions to operate. If we did not yet obtain runtime
    // permission on Android M and above, now is a good time to ask the user for it.
    if (CameraPermissionHelper.hasCameraPermission(this)) {
      showLoadingMessage();
      // Note that order matters - see the note in onPause(), the reverse applies here.
      mSession.resume(mDefaultConfig);
      scene.bind();
    } else {
      CameraPermissionHelper.requestCameraPermission(this);
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    // Note that the order matters - GLSurfaceView is paused first so that it does not try
    // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
    // still call mSession.update() and get a SessionPausedException.
    scene.unbind();
    mSession.pause();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
    if (!CameraPermissionHelper.hasCameraPermission(this)) {
      Toast.makeText(this,
          "Camera permission is needed to run this application", Toast.LENGTH_LONG).show();
      finish();
    }
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) {
      // Standard Android full-screen functionality.
      getWindow().getDecorView().setSystemUiVisibility(
          View.SYSTEM_UI_FLAG_LAYOUT_STABLE
              | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
              | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_FULLSCREEN
              | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
  }

  private void handleTap(Frame frame) {
    // Handle taps. Handling only one tap per frame, as taps are usually low frequency
    // compared to frame rate.
    MotionEvent tap = mQueuedSingleTaps.poll();
    if (tap != null && frame.getTrackingState() == TrackingState.TRACKING) {
      for (HitResult hit : frame.hitTest(tap)) {
        // Check if any plane was hit, and if it was hit inside the plane polygon.
        if (hit instanceof PlaneHitResult && ((PlaneHitResult) hit).isHitInPolygon()) {
          scene.addTouch(hit);

          // Hits are sorted by depth. Consider only closest hit on a plane.
          break;
        }
      }
    }
  }

  private void showLoadingMessage() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        mLoadingMessageSnackbar = Snackbar.make(
            HelloArActivity.this.findViewById(android.R.id.content),
            "Searching for surfaces...", Snackbar.LENGTH_INDEFINITE);
        mLoadingMessageSnackbar.getView().setBackgroundColor(0xbf323232);
        mLoadingMessageSnackbar.show();
      }
    });
  }

  private void hideLoadingMessage() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (mLoadingMessageSnackbar != null) {
          mLoadingMessageSnackbar.dismiss();
          mLoadingMessageSnackbar = null;
        }
      }
    });
  }
}
