package com.contentful.eggar.rendering;


import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import com.google.ar.core.Anchor;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.NotTrackingException;

import com.contentful.eggar.DisplayRotationHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class Scene implements GLSurfaceView.Renderer {
  private static final String TAG = Scene.class.getSimpleName();
  // Temporary matrix allocated here to reduce number of allocations for each frame.
  private CameraFeedRenderer cameraFeedRenderer = new CameraFeedRenderer();
  private List<ObjectRenderer> objectRenderer = new ArrayList<>();
  private PlaneRenderer planeRenderer = new PlaneRenderer();
  private PointCloudRenderer pointCloudRenderer = new PointCloudRenderer();
  private Context context;
  private GLSurfaceView surfaceView;
  private Session session;
  private DrawingCallback callback;
  private DisplayRotationHelper mDisplayRotationHelper;
  private float scaleFactor = 1.0f;

  public Scene(Context context, GLSurfaceView surfaceView, DrawingCallback callback) {
    // Set up renderer.
    this.context = context;
    surfaceView.setPreserveEGLContextOnPause(true);
    surfaceView.setEGLContextClientVersion(2);
    surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
    surfaceView.setRenderer(this);
    surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    this.surfaceView = surfaceView;
    this.mDisplayRotationHelper = new DisplayRotationHelper(context);

    this.callback = callback;
  }

  public void bind(Session session) {
    this.session = session;
    surfaceView.onResume();
    mDisplayRotationHelper.onResume();
  }

  public void unbind() {
    surfaceView.onPause();
    mDisplayRotationHelper.onPause();
  }

  @Override
  public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

    // Create the texture and pass it to ARCore session to be filled during update().
    cameraFeedRenderer.createOnGlThread(context);

    // Prepare the other rendering objects.
    try {
      planeRenderer.createOnGlThread(context, "trigrid.png");
    } catch (IOException e) {
      Log.e(TAG, "Failed to read plane texture");
    }
    pointCloudRenderer.createOnGlThread(context);
  }

  @Override
  public void onSurfaceChanged(GL10 gl, int width, int height) {
    GLES20.glViewport(0, 0, width, height);
    mDisplayRotationHelper.onSurfaceChanged(width, height);
  }

  @Override
  public void onDrawFrame(GL10 gl) {
    if (session == null) {
      return;
    }

    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

    mDisplayRotationHelper.updateSessionIfNeeded(session);
    session.setCameraTextureName(cameraFeedRenderer.getTextureId());

    final Frame frame = session.update();
    if (callback != null) {
      callback.onDraw(frame);
    }

    // Draw background.
    cameraFeedRenderer.draw(frame);

    // If not tracking, don't draw 3d objects.
    Camera camera = frame.getCamera();
    if (camera.getTrackingState() != TrackingState.TRACKING) {
      return;
    }

    // Get projection matrix.
    float[] projmtx = new float[16];
    camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

    // Get camera matrix and draw.
    float[] viewmtx = new float[16];
    camera.getViewMatrix(viewmtx, 0);

    // Compute lighting from average intensity of the image.
    final float lightIntensity = frame.getLightEstimate().getPixelIntensity();

    // Visualize tracked points.
    PointCloud pointCloud = frame.acquirePointCloud();
    pointCloudRenderer.update(pointCloud);
    pointCloudRenderer.draw(viewmtx, projmtx);
    pointCloud.release();

    // Check if we detected at least one plane. If so, hide the loading message.
    if (callback != null) {
      for (Plane plane : session.getAllTrackables(Plane.class)) {
        if (plane.getType() == com.google.ar.core.Plane.Type.HORIZONTAL_UPWARD_FACING &&
            plane.getTrackingState() == TrackingState.TRACKING) {
          callback.trackingPlane();
          break;
        }
      }
      for (Point point : session.getAllTrackables(Point.class)) {
        if (point.getOrientationMode() == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL &&
            point.getTrackingState() == TrackingState.TRACKING) {
          callback.trackingPlane();
          break;
        }
      }
    }

    // Visualize planes.
    planeRenderer.drawPlanes(session.getAllTrackables(Plane.class), camera.getPose(), projmtx);

    // Visualize anchors created by touch.
    for (final ObjectRenderer renderer : objectRenderer) {
      if (!renderer.isTracking()) {
        continue;
      }

      // Update and draw each model.
      renderer.updateModelMatrix(scaleFactor);
      renderer.draw(viewmtx, projmtx, lightIntensity);
    }
  }

  public int getRendererCount() {
    return objectRenderer.size();
  }

  public void addRenderer(ObjectRenderer renderer, Trackable trackable, Anchor anchor) {
    // Cap the number of objects created. This avoids overloading both the
    // rendering system and ARCore.
    if (objectRenderer.size() >= 16) {
      objectRenderer.get(0).destroy(session);
      objectRenderer.remove(0);
    }

    // Adding an Anchor tells ARCore that it should track this position in
    // space. This anchor will be used in PlaneAttachment to place the 3d model
    // in the correct position relative both to the world and to the plane.
    try {
      renderer.setAttachement(
          new TrackableAttachment(
              trackable,
              anchor)
      );
    } catch (NotTrackingException e) {
      Log.e(TAG, "Session is not tracking.");
    }

    objectRenderer.add(renderer);
  }

  public float getScaleFactor() {
    return scaleFactor;
  }

  public void setScaleFactor(float scaleFactor) {
    this.scaleFactor = scaleFactor;
  }

  public interface DrawingCallback {
    void onDraw(Frame frame);

    void trackingPlane();
  }
}
