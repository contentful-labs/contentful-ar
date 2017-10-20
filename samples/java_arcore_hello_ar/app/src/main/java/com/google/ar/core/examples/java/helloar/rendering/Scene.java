package com.google.ar.core.examples.java.helloar.rendering;


import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import com.google.ar.core.Frame;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.CameraException;
import com.google.ar.core.exceptions.NotTrackingException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class Scene implements GLSurfaceView.Renderer {
  private static final String TAG = Scene.class.getSimpleName();
  // Temporary matrix allocated here to reduce number of allocations for each frame.
  private final float[] anchorMatrix = new float[16];
  private CameraFeedRenderer cameraFeedRenderer = new CameraFeedRenderer();
  private ObjectRenderer objectRenderer = new ObjectRenderer();
  private ObjectRenderer objectShadowRenderer = new ObjectRenderer();
  private PlaneRenderer planeRenderer = new PlaneRenderer();
  private PointCloudRenderer pointCloudRenderer = new PointCloudRenderer();
  private Context context;
  private GLSurfaceView surfaceView;
  private Session session;
  private DrawingCallback callback;
  private ArrayList<PlaneAttachment> attachments = new ArrayList<>();

  public Scene(Context context, GLSurfaceView surfaceView, Session session, DrawingCallback callback) {
    // Set up renderer.
    this.context = context;
    surfaceView.setPreserveEGLContextOnPause(true);
    surfaceView.setEGLContextClientVersion(2);
    surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
    surfaceView.setRenderer(this);
    surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    this.surfaceView = surfaceView;

    this.session = session;
    this.callback = callback;
  }

  public void bind() {
    surfaceView.onResume();
  }

  public void unbind() {
    surfaceView.onPause();
  }

  @Override
  public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

    // Create the texture and pass it to ARCore session to be filled during update().
    cameraFeedRenderer.createOnGlThread(context);
    session.setCameraTextureName(cameraFeedRenderer.getTextureId());

    // Prepare the other rendering objects.
    try {
      objectRenderer.createOnGlThread(context, "andy.obj", "andy.png");
      objectRenderer.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f);

      objectShadowRenderer.createOnGlThread(context,
          "andy_shadow.obj", "andy_shadow.png");
      objectShadowRenderer.setBlendMode(ObjectRenderer.BlendMode.Shadow);
      objectShadowRenderer.setMaterialProperties(1.0f, 0.0f, 0.0f, 1.0f);
    } catch (IOException e) {
      Log.e(TAG, "Failed to read obj file");
    }

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
    // Notify ARCore session that the view size changed so that the perspective matrix and
    // the video background can be properly adjusted.
    session.setDisplayGeometry(width, height);
  }

  @Override
  public void onDrawFrame(GL10 gl) {
    // Clear screen to notify driver it should not load any pixels from previous frame.
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

    // Obtain the current frame from ARSession. When the configuration is set to
    // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
    // camera framerate.
    Frame frame = null;
    try {
      frame = session.update();
    } catch (CameraException e) {
      Log.e(TAG, "Could not update the session.", e);
      return;
    }

    if (callback != null) {
      callback.onDraw(frame);
    }

    // Draw background.
    cameraFeedRenderer.draw(frame);

    // If not tracking, don't draw 3d objects.
    if (frame.getTrackingState() == Frame.TrackingState.NOT_TRACKING) {
      return;
    }

    // Get projection matrix.
    float[] projmtx = new float[16];
    session.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

    // Get camera matrix and draw.
    float[] viewmtx = new float[16];
    frame.getViewMatrix(viewmtx, 0);

    // Compute lighting from average intensity of the image.
    final float lightIntensity = frame.getLightEstimate().getPixelIntensity();

    // Visualize tracked points.
    pointCloudRenderer.update(frame.getPointCloud());
    pointCloudRenderer.draw(frame.getPointCloudPose(), viewmtx, projmtx);

    // Check if we detected at least one plane. If so, hide the loading message.
    if (callback != null) {
      for (Plane plane : session.getAllPlanes()) {
        if (plane.getType() == com.google.ar.core.Plane.Type.HORIZONTAL_UPWARD_FACING &&
            plane.getTrackingState() == Plane.TrackingState.TRACKING) {
          callback.trackingPlane();
          break;
        }
      }
    }

    // Visualize planes.
    planeRenderer.drawPlanes(session.getAllPlanes(), frame.getPose(), projmtx);

    // Visualize anchors created by touch.
    float scaleFactor = 1.0f;
    for (PlaneAttachment planeAttachment : attachments) {
      if (!planeAttachment.isTracking()) {
        continue;
      }
      // Get the current combined pose of an Anchor and Plane in world space. The Anchor
      // and Plane poses are updated during calls to session.update() as ARCore refines
      // its estimate of the world.
      planeAttachment.getPose().toMatrix(anchorMatrix, 0);

      // Update and draw the model and its shadow.
      objectRenderer.updateModelMatrix(anchorMatrix, scaleFactor);
      objectShadowRenderer.updateModelMatrix(anchorMatrix, scaleFactor);
      objectRenderer.draw(viewmtx, projmtx, lightIntensity);
      objectShadowRenderer.draw(viewmtx, projmtx, lightIntensity);
    }
  }

  public void addAttachment(Plane plane, Pose pose) {
    // Cap the number of objects created. This avoids overloading both the
    // rendering system and ARCore.
    if (attachments.size() >= 16) {
      session.removeAnchors(Arrays.asList(attachments.get(0).getAnchor()));
      attachments.remove(0);
    }
    // Adding an Anchor tells ARCore that it should track this position in
    // space. This anchor will be used in PlaneAttachment to place the 3d model
    // in the correct position relative both to the world and to the plane.
    try {
      attachments.add(new PlaneAttachment(
          plane,
          session.addAnchor(pose)));
    } catch (NotTrackingException e) {
      Log.e(TAG, "Session is not tracking.");
    }
  }

  public interface DrawingCallback {
    void onDraw(Frame frame);

    void trackingPlane();
  }
}
