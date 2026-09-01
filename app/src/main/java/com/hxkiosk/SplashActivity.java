package com.hxkiosk;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        PreferenceManager preferenceManager = new PreferenceManager(this);
        Class<?> destination = preferenceManager.isSetupCompleted()
                ? MainKioskActivity.class
                : SetupActivity.class;

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, destination));
            finish();
        }, 500L);
    }
}
