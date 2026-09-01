package com.hxkiosk;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class ScreenCaptureConsentActivity extends AppCompatActivity {

    private static final int REQUEST_SCREEN_CAPTURE = 71;
    private static boolean askedThisProcess;

    public static boolean shouldAsk() {
        return !askedThisProcess && !ScreenMirrorService.isCapturing();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (ScreenMirrorService.isCapturing()) {
            finish();
            return;
        }
        askedThisProcess = true;
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (manager == null) {
            finish();
            return;
        }
        Intent captureIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            captureIntent = manager.createScreenCaptureIntent(
                    MediaProjectionConfig.createConfigForDefaultDisplay()
            );
        } else {
            captureIntent = manager.createScreenCaptureIntent();
        }
        startActivityForResult(captureIntent, REQUEST_SCREEN_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SCREEN_CAPTURE && resultCode == Activity.RESULT_OK && data != null) {
            ScreenMirrorService.start(this, resultCode, data);
        } else {
            askedThisProcess = false;
        }
        finish();
    }
}
