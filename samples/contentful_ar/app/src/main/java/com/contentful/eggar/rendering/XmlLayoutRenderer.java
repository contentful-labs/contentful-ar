package com.contentful.eggar.rendering;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.hardware.display.DisplayManager;
import android.support.annotation.LayoutRes;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;

import java.io.FileNotFoundException;

import static com.contentful.eggar.rendering.ShaderUtil.normalizeFileName;

public class XmlLayoutRenderer extends ObjectRenderer {
  private Bitmap bitmap;

  public XmlLayoutRenderer(Context context, @LayoutRes int xmlLayoutResource) {
    super(
        normalizeFileName("plane.obj", basepath(context)),
        "",
        normalizeFileName(ObjectRendererFactory.DEFAULT_FRAGMENT_SHADER_FILE_NAME, basepath(context)),
        normalizeFileName(ObjectRendererFactory.DEFAULT_VERTEX_SHADER_FILE_NAME, basepath(context)));

    loadTexture(context, xmlLayoutResource);

    setBlendMode(BlendMode.Grid);
  }

  private static String basepath(Context context) {
    return context.getExternalFilesDir(null).getAbsolutePath();
  }

  @Override protected Bitmap readTexture() throws FileNotFoundException {
    return bitmap;
  }

  private void loadTexture(Context context, @LayoutRes int layout) {
    final DisplayMetrics displayMetrics = new DisplayMetrics();
    final DisplayManager manager = context.getSystemService(DisplayManager.class);
    manager.getDisplays()[0].getMetrics(displayMetrics);
    final int height = displayMetrics.heightPixels;
    final int width = displayMetrics.widthPixels;

    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    final Canvas canvas = new Canvas(bitmap);

    int measuredWidth = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
    int measuredHeight = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY);

    LayoutInflater inflater = LayoutInflater.from(context);
    View view = inflater.inflate(layout, null, false);

    view.measure(measuredWidth, measuredHeight);
    view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    view.draw(canvas);
  }
}
