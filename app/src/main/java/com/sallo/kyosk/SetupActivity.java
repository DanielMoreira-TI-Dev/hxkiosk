package com.sallo.kyosk;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class SetupActivity extends AppCompatActivity {

    private EditText passwordField;
    private EditText confirmPasswordField;
    private PreferenceManager preferenceManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        preferenceManager = new PreferenceManager(this);
        if (preferenceManager.isSetupCompleted()) {
            openMainKiosk();
            return;
        }

        passwordField = findViewById(R.id.setupPasswordField);
        confirmPasswordField = findViewById(R.id.setupConfirmPasswordField);
        MaterialButton saveButton = findViewById(R.id.setupSaveButton);
        saveButton.setOnClickListener(v -> validateAndSave());
    }

    private void validateAndSave() {
        String password = passwordField.getText().toString().trim();
        String confirmation = confirmPasswordField.getText().toString().trim();

        if (TextUtils.isEmpty(password)) {
            passwordField.setError(getString(R.string.error_password_required));
            return;
        }

        if (password.length() < 4) {
            passwordField.setError(getString(R.string.error_password_length));
            return;
        }

        if (!TextUtils.equals(password, confirmation)) {
            confirmPasswordField.setError(getString(R.string.error_password_mismatch));
            return;
        }

        preferenceManager.saveAdminPassword(password);
        preferenceManager.setKioskMode(PreferenceManager.MODE_LINK);
        preferenceManager.setAllowedUrl(preferenceManager.getAllowedUrl());
        preferenceManager.setLauncherName(preferenceManager.getLauncherName());
        preferenceManager.setPrimaryColor(preferenceManager.getPrimaryColor());
        preferenceManager.setShowLogo(true);
        preferenceManager.setGridColumns(preferenceManager.getGridColumns());

        Toast.makeText(this, R.string.kiosk_initialized, Toast.LENGTH_SHORT).show();
        openMainKiosk();
    }

    private void openMainKiosk() {
        Intent intent = new Intent(this, MainKioskActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
