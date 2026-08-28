package com.sallo.kyosk;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class ExitKioskActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exit_kiosk);

        findViewById(R.id.cancelExitButton).setOnClickListener(v -> finish());
        findViewById(R.id.confirmExitButton).setOnClickListener(v -> exitKiosk());
    }

    private void exitKiosk() {
        KioskPolicyManager.stopLockTaskIfNeeded(this);
        if (KioskPolicyManager.isHomeApp(this)) {
            Toast.makeText(this, R.string.exit_launcher_settings_message, Toast.LENGTH_LONG).show();
            KioskPolicyManager.openHomeSettings(this);
            finish();
            return;
        }

        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(homeIntent);
        finishAffinity();
    }
}
