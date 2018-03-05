package com.contentful.ar.io;


import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.contentful.ar.vault.models.Model;
import com.contentful.vault.Asset;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AssetsSaver {
  private static final String TAG = AssetsSaver.class.getSimpleName();

  public static void copyApkAssetsToSdcard(Context context) {
    final AssetManager assets = context.getAssets();
    final String[] assetArray;
    try {
      assetArray = assets.list("");
    } catch (IOException e) {
      Log.e(TAG, "Could not list assets.", e);
      return;
    }

    final File outputDir = context.getExternalFilesDir(null);
    if (outputDir == null) {
      Log.e(TAG, "Could not find default external directory");
      return;
    }

    for (final String file : assetArray) {
      final String localCopyName = outputDir.getAbsolutePath() + "/" + file;

      // ignore files without an extension (mostly folders)
      if (!localCopyName.contains("")) {
        continue;
      }

      OutputStream outputStream;
      try {
        outputStream = new FileOutputStream(localCopyName);
      } catch (FileNotFoundException e) {
        Log.e(TAG, "Could not open copy file: '" + localCopyName + "'.");
        outputStream = null;
      }

      if (outputStream != null) {
        try {
          IOUtils.copy(assets.open(file), outputStream);
        } catch (IOException e) {
          Log.i(TAG, "Could not open asset file: '" + file + "'.");
        }
      }
    }
  }

  public static void downloadContentfulAssetsToSdCard(Context context, Model model) {
    final File outputDir = context.getExternalFilesDir(null);
    if (outputDir == null) {
      Log.e(TAG, "Could not find default external directory");
      return;
    }

    final String title = model.title();

    final String localObjFile = outputDir.getAbsolutePath() + "/" + title + ".obj";
    final String localTextureFile = outputDir.getAbsolutePath() + "/" + title + ".png";
    try {
      saveAsset(model.model(), localObjFile);
      saveAsset(model.texture(), localTextureFile);
    } catch (FileNotFoundException e) {
      Log.e(TAG, "Could not open copy file: '" + localObjFile + "'.");
    } catch (IllegalStateException e) {
      Log.e(TAG, "Could not download asset: '" + e.getMessage() + "'.");
    } catch (IOException e) {
      Log.i(TAG, "Could not download asset: '" + model.model().url() + "'.");
    }
  }

  private static void saveAsset(Asset asset, String localObjFile) throws IOException {
    final OutputStream modelOutput = new FileOutputStream(localObjFile);

    final Response response = fetchAsset("https:" + asset.file().get("url"));
    IOUtils.copy(response.body().byteStream(), modelOutput);
  }

  private static Response fetchAsset(String url) throws IOException {
    final Response response = new OkHttpClient.Builder()
        .build()
        .newCall(
            new Request.Builder()
                .url(url)
                .build()
        ).execute();

    if (response.isSuccessful()) {
      return response;
    } else {
      throw new IllegalStateException(url);
    }
  }
}
