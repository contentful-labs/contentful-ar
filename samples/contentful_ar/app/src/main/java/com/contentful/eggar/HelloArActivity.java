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

package com.contentful.eggar;

import android.content.DialogInterface;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.contentful.eggar.io.AssetsSaver;
import com.contentful.eggar.rendering.ObjectRenderer;
import com.contentful.eggar.rendering.ObjectRendererFactory;
import com.contentful.eggar.rendering.Scene;
import com.contentful.eggar.rendering.XmlLayoutRenderer;
import com.contentful.eggar.vault.VaultManager;
import com.contentful.eggar.vault.models.Model;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Predicate;

/**
 * This is a simple example that shows how to create an augmented reality (AR) application using
 * the ARCore API. The application will display any detected planes and will allow the user to
 * tap on a plane to place a 3d model of the Android robot.
 */
public class HelloArActivity extends AppCompatActivity {
  private static final String TAG = HelloArActivity.class.getSimpleName();
  public static final String XML_UI_MODEL_NAME = "XML UI";

  // Rendering. The Renderers are created here, and initialized when the GL surface is created.
  private GLSurfaceView mSurfaceView;
  private Scene scene;
  private Config defaultConfig;
  private Session session;
  private VaultManager vaultManager;
  private Snackbar loadingMessageSnackbar = null;

  // Tap handling and UI.
  private ArrayBlockingQueue<MotionEvent> queuedTaps = new ArrayBlockingQueue<>(16);
  private String nextObject = "parrot.obj";

  private ObjectRendererFactory objectFactory;
  private boolean installRequested = false;

  private List<String> models = new ArrayList<>();

  private final View.OnTouchListener tapListener = new View.OnTouchListener() {
    @Override
    public boolean onTouch(View v, MotionEvent event) {
      if (event.getAction() == MotionEvent.ACTION_UP) {
        // Queue tap if there is space. Tap is lost if queue is full.
        queuedTaps.offer(event);
      }
      return true;
    }
  };

  private final Scene.DrawingCallback drawCallback = new Scene.DrawingCallback() {
    @Override
    public void onDraw(Frame frame) {
      handleTap(frame);
    }

    @Override
    public void trackingPlane() {
      hideLoadingMessage();
    }
  };

  private View.OnClickListener offlineButtonClicked = new View.OnClickListener() {
    @Override
    public void onClick(View view) {
      final List<String> assets = new ArrayList<>();

      try {
        assets.addAll(Arrays.asList(getAssets().list("")));
      } catch (IOException e) {
        Log.e(TAG, "Could not list assets.", e);
        return;
      }
      assets.removeIf(new Predicate<String>() {
        @Override public boolean test(String s) {
          return !s.contains(".obj");
        }
      });
      assets.add(XML_UI_MODEL_NAME);

      final String items[] = new String[assets.size()];
      for (int i = 0; i < items.length; ++i) {
        items[i] = assets.get(i);
      }

      new AlertDialog.Builder(HelloArActivity.this)
          .setTitle("Select build-in models")
          .setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
              String selection = assets.get(which);
              if (XML_UI_MODEL_NAME.equals(selection)) {
                selection = "";
              }
              nextObject = selection;
            }
          }).show();
    }
  };

  private View.OnClickListener contentfulButtonClicked = new View.OnClickListener() {
    @Override
    public void onClick(View view) {
      final String items[] = new String[models.size()];
      for (int i = 0; i < items.length; ++i) {
        items[i] = models.get(i) + ".obj";
      }

      new AlertDialog.Builder(HelloArActivity.this)
          .setTitle("Select contentful models")
          .setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
              nextObject = models.get(which) + ".obj";
            }
          }).show();
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    mSurfaceView = findViewById(R.id.surfaceview);

    setupButtons();

    objectFactory = new ObjectRendererFactory(getExternalFilesDir(null).getAbsolutePath());
    scene = new Scene(this, mSurfaceView, drawCallback);

    // Set up tap listener.
    mSurfaceView.setOnTouchListener(tapListener);

    vaultManager = new VaultManager();

    AssetsSaver.copyApkAssetsToSdcard(this);
  }

  @Override
  protected void onResume() {
    super.onResume();

    // Check if AR Core is installed
    switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
      case INSTALL_REQUESTED:
        installRequested = true;
        return;
      case INSTALLED:
        break;
    }

    // ARCore requires camera permissions to operate. If we did not yet obtain runtime
    // permission on Android M and above, now is a good time to ask the user for it.
    if (CameraPermissionHelper.hasCameraPermission(this)) {
      showLoadingMessage();
      // Note that order matters - see the note in onPause(), the reverse applies here.
      session = new Session(this);
      // Create default config, check is supported, create session from that config.
      defaultConfig = new Config(session);
      if (!session.isSupported(defaultConfig)) {
        Toast.makeText(this, "This device does not support AR", Toast.LENGTH_LONG).show();
        finish();
        return;
      }

      session.resume();
      scene.bind(session);
    } else {
      CameraPermissionHelper.requestCameraPermission(this);
    }

    vaultManager.bind(this, new VaultManager.Listener() {
      @Override public void onModelReceived(Model model) {
        info(model.title());
        if (!models.contains(model.title())) {
          models.add(model.title());
        }
      }

      @Override public void error(String message) {
        Log.e(TAG, message);
      }

      @Override public void info(String message) {
        Log.i(TAG, message);
      }
    });
  }

  @Override
  public void onPause() {
    super.onPause();
    // Note that the order matters - GLSurfaceView is paused first so that it does not try
    // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
    // still call session.update() and create a SessionPausedException.
    scene.unbind();
    if (session != null) {
      session.pause();
    }

    if (vaultManager != null) {
      vaultManager.unbind();
    }
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
    MotionEvent tap = queuedTaps.poll();
    Camera camera = frame.getCamera();
    if (tap != null
        && tap.getAction() == MotionEvent.ACTION_UP
        && camera.getTrackingState() == TrackingState.TRACKING) {
      for (HitResult hit : frame.hitTest(tap)) {
        // Check if any plane was hit, and if it was hit inside the plane polygon.
        Trackable trackable = hit.getTrackable();
        if ((trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose()))
            || (trackable instanceof Point
            && ((Point) trackable).getOrientationMode()
            == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL)) {

          final ObjectRenderer shadow = objectFactory.create("andy_shadow.obj");
          if (shadow != null) {
            shadow.setBlendMode(ObjectRenderer.BlendMode.Shadow);
            scene.addRenderer(
                shadow,
                trackable,
                hit.createAnchor()
            );
          }

          final ObjectRenderer object;
          if (nextObject.length() != 0) {
            object = objectFactory.create(nextObject);
          } else {
            object = new XmlLayoutRenderer(getApplicationContext(), R.layout.ar_sample_layout);
          }

          if (object != null) {
            scene.addRenderer(
                object,
                trackable,
                hit.createAnchor()
            );
          }

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
        loadingMessageSnackbar = Snackbar.make(
            HelloArActivity.this.findViewById(android.R.id.content),
            "Searching for surfaces...", Snackbar.LENGTH_INDEFINITE);
        loadingMessageSnackbar.getView().setBackgroundColor(0xbf323232);
        loadingMessageSnackbar.show();
      }
    });
  }

  private void hideLoadingMessage() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (loadingMessageSnackbar != null) {
          loadingMessageSnackbar.dismiss();
          loadingMessageSnackbar = null;
        }
      }
    });
  }


  private void setupButtons() {
    findViewById(R.id.main_button_offline).setOnClickListener(offlineButtonClicked);
    findViewById(R.id.main_button_contentful).setOnClickListener(contentfulButtonClicked);
    findViewById(R.id.main_button_plus).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        scene.setScaleFactor(scene.getScaleFactor() * 1.5f);
      }
    });
    findViewById(R.id.main_button_minus).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        scene.setScaleFactor(scene.getScaleFactor() / 1.5f);
      }
    });
  }
}
