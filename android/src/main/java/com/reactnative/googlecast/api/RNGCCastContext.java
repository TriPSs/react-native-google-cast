package com.reactnative.googlecast.api;

import android.app.UiModeManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Handler;
import android.view.View;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.mediarouter.app.MediaRouteButton;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.common.annotations.VisibleForTesting;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.IntroductoryOverlay;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.reactnative.googlecast.components.GoogleCastExpandedControlsActivity;
import com.reactnative.googlecast.components.RNGoogleCastButtonManager;
import com.reactnative.googlecast.types.RNGCCastState;

import java.util.HashMap;
import java.util.Map;

import static android.content.Context.UI_MODE_SERVICE;

public class RNGCCastContext
    extends ReactContextBaseJavaModule implements LifecycleEventListener {

  @VisibleForTesting
  public static final String REACT_CLASS = "RNGCCastContext";

  public static final String SESSION_STARTING = "GoogleCast:SessionStarting";
  public static final String SESSION_STARTED = "GoogleCast:SessionStarted";
  public static final String SESSION_START_FAILED =
      "GoogleCast:SessionStartFailed";
  public static final String SESSION_SUSPENDED = "GoogleCast:SessionSuspended";
  public static final String SESSION_RESUMING = "GoogleCast:SessionResuming";
  public static final String SESSION_RESUMED = "GoogleCast:SessionResumed";
  public static final String SESSION_ENDING = "GoogleCast:SessionEnding";
  public static final String SESSION_ENDED = "GoogleCast:SessionEnded";

  private SessionManagerListener<CastSession> mSessionManagerListener;
  private boolean isTV;

  public static CastContext getSharedInstance(@NonNull Context context) {
    if (!RNGCCastContext.isTV(context)) {
      return CastContext.getSharedInstance(context);
    }

    return null;
  }

  public static boolean isTV(@NonNull Context context) {
    UiModeManager uiModeManager = (UiModeManager) context.getSystemService(UI_MODE_SERVICE);

    try {
      return uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;

    } catch (NullPointerException exception) {
      return false;
    }
  }

  public RNGCCastContext(ReactApplicationContext reactContext) {
    super(reactContext);

    this.isTV = RNGCCastContext.isTV(reactContext);

    reactContext.addLifecycleEventListener(this);
    setupCastListener();
  }

  @Override
  public String getName() {
    return REACT_CLASS;
  }

  @Override
  public Map<String, Object> getConstants() {
    final Map<String, Object> constants = new HashMap<>();

    constants.put("SESSION_STARTING", SESSION_STARTING);
    constants.put("SESSION_STARTED", SESSION_STARTED);
    constants.put("SESSION_START_FAILED", SESSION_START_FAILED);
    constants.put("SESSION_SUSPENDED", SESSION_SUSPENDED);
    constants.put("SESSION_RESUMING", SESSION_RESUMING);
    constants.put("SESSION_RESUMED", SESSION_RESUMED);
    constants.put("SESSION_ENDING", SESSION_ENDING);
    constants.put("SESSION_ENDED", SESSION_ENDED);

    return constants;
  }

  public void sendEvent(String eventName) {
    this.sendEvent(eventName, null);
  }

  public void sendEvent(String eventName, @Nullable WritableMap params) {
    getReactApplicationContext()
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName, params);
  }

  @ReactMethod
  public void getCastState(final Promise promise) {
    if (this.isTV) {
      promise.resolve(null);

    } else {
      getReactApplicationContext().runOnUiQueueThread(new Runnable() {
        @Override
        public void run() {
          CastContext castContext =
              CastContext.getSharedInstance(getReactApplicationContext());
          promise.resolve(RNGCCastState.toJson(castContext.getCastState()));
        }
      });
    }
  }

  @ReactMethod
  public void endSession(final boolean stopCasting, final Promise promise) {
    getReactApplicationContext().runOnUiQueueThread(new Runnable() {
      @Override
      public void run() {
        SessionManager sessionManager =
            CastContext.getSharedInstance(getReactApplicationContext())
                .getSessionManager();
        sessionManager.endCurrentSession(stopCasting);
        promise.resolve(true);
      }
    });
  }

  @ReactMethod
  public void showCastDialog(final Promise promise) {
    getReactApplicationContext().runOnUiQueueThread(new Runnable() {
      @Override
      public void run() {
        MediaRouteButton button = RNGoogleCastButtonManager.getCurrent();
        if (button != null) {
          button.performClick();
          promise.resolve(true);
        } else {
          promise.resolve(false);
        }
      }
    });
  }

  @ReactMethod
  public void showExpandedControls() {
    ReactApplicationContext context = getReactApplicationContext();
    Intent intent =
        new Intent(context, GoogleCastExpandedControlsActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(intent);
  }

  @ReactMethod
  public void showIntroductoryOverlay(final ReadableMap options, final Promise promise) {
    final MediaRouteButton button = RNGoogleCastButtonManager.getCurrent();

    if ((button != null) && button.getVisibility() == View.VISIBLE) {
      new Handler().post(new Runnable() {
        @Override
        public void run() {
          IntroductoryOverlay.Builder builder = new IntroductoryOverlay.Builder(getCurrentActivity(), button);

          if (options.getBoolean("once")) {
            builder.setSingleTime();
          }

          builder.setOnOverlayDismissedListener(
              new IntroductoryOverlay.OnOverlayDismissedListener() {
                @Override
                public void onOverlayDismissed() {
                  promise.resolve(true);
                }
              });

          IntroductoryOverlay overlay = builder.build();

          overlay.show();
        }
      });
    }
  }

  private void setupCastListener() {
    mSessionManagerListener = new RNGCSessionManagerListener(this);
  }

  @Override
  public void onHostResume() {
    if (!this.isTV) {
      getReactApplicationContext().runOnUiQueueThread(new Runnable() {
        @Override
        public void run() {
          SessionManager sessionManager =
              CastContext.getSharedInstance(getReactApplicationContext())
                  .getSessionManager();
          sessionManager.addSessionManagerListener(mSessionManagerListener,
              CastSession.class);
        }
      });
    }
  }

  @Override
  public void onHostPause() {
    if (!this.isTV) {
      getReactApplicationContext().runOnUiQueueThread(new Runnable() {
        @Override
        public void run() {
          SessionManager sessionManager =
              CastContext.getSharedInstance(getReactApplicationContext())
                  .getSessionManager();
          sessionManager.removeSessionManagerListener(mSessionManagerListener,
              CastSession.class);
        }
      });
    }
  }

  @Override
  public void onHostDestroy() {
  }

  public void runOnUiQueueThread(Runnable runnable) {
    getReactApplicationContext().runOnUiQueueThread(runnable);
  }

}
