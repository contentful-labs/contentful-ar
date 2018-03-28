package com.contentful.eggar.vault;


import android.content.Context;
import android.util.Log;

import com.contentful.eggar.io.AssetsSaver;
import com.contentful.eggar.vault.models.Model;
import com.contentful.eggar.vault.models.VaultSpace;
import com.contentful.vault.SyncCallback;
import com.contentful.vault.SyncConfig;
import com.contentful.vault.SyncResult;
import com.contentful.vault.Vault;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

public class VaultManager {

  public interface Listener {
    void onModelReceived(Model model);

    void error(String message);

    void info(String message);
  }

  private static final String TAG = VaultManager.class.getSimpleName();

  private Vault vault;
  private SyncConfig config;
  private Listener listener;
  private Disposable disposable;

  private Consumer<Model> modelConsumer = new Consumer<Model>() {
    @Override public void accept(Model model) throws Exception {
      if (listener != null) {
        listener.onModelReceived(model);
      }
    }
  };

  public void bind(final Context context, Listener listener) {
    this.listener = listener;

    if (vault == null) {
      config = new SyncConfig.Builder()
          .setAccessToken(VaultSpace.ACCESS_TOKEN)
          .setSpaceId(VaultSpace.SPACE_ID)
          .build();

      vault = Vault.with(context, VaultSpace.class);
    }

    vault.requestSync(config, new SyncCallback() {
      @Override public void onResult(SyncResult result) {
        if (result.isSuccessful()) {
          info("Synced successfully.");
        } else {
          error("Could not sync: '" + result.error().toString() + "'.");
        }
      }
    });

    disposable = vault.observe(Model.class)
        .all()
        .map(new Function<Model, Model>() {
          @Override public Model apply(Model model) throws Exception {
            AssetsSaver.downloadContentfulAssetsToSdCard(context, model);
            return model;
          }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(modelConsumer);
  }

  public void unbind() {
    if (disposable != null && disposable.isDisposed()) {
      disposable.dispose();
    }
  }

  private void info(String message) {
    if (listener != null) {
      listener.info(message);
    }
    Log.i(TAG, message);
  }

  private void error(String message) {
    if (listener != null) {
      listener.error(message);
    }
    Log.e(TAG, message);
  }
}
