package com.hxkiosk;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;

public class HxKioskApp extends Application {

    private static HxKioskApp instance;
    private WeakReference<Activity> resumedActivity = new WeakReference<>(null);

    public static HxKioskApp get() {
        return instance;
    }

    @Nullable
    public Activity getResumedActivity() {
        return resumedActivity.get();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                resumedActivity = new WeakReference<>(activity);
                syncRemoteAccessService();
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                Activity current = resumedActivity.get();
                if (current == activity) {
                    resumedActivity = new WeakReference<>(null);
                }
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
            }
        });
    }

    public void syncRemoteAccessService() {
        PreferenceManager preferenceManager = new PreferenceManager(this);
        if (preferenceManager.isSetupCompleted()
                && preferenceManager.getBooleanConfig(PreferenceManager.KEY_REMOTE_ACCESS, true)) {
            try {
                RemoteAccessService.start(this);
            } catch (RuntimeException ignored) {
                // O Android pode recusar o servico em segundo plano; o painel admin inicia de novo.
            }
        } else {
            RemoteAccessService.stop(this);
            ScreenMirrorService.stop(this);
        }
    }
}
