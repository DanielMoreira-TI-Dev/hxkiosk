package com.sallo.kyosk;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class AdminAccessActivity extends AppCompatActivity {

    public static final String EXTRA_OPEN_EXIT_AFTER_AUTH = "open_exit_after_auth";

    private EditText passwordField;
    private TextView errorView;
    private TextView subtitleView;
    private PreferenceManager preferenceManager;
    private boolean openExitAfterAuth;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_access);

        preferenceManager = new PreferenceManager(this);
        passwordField = findViewById(R.id.adminPasswordField);
        errorView = findViewById(R.id.adminAccessError);
        subtitleView = findViewById(R.id.adminAccessSubtitle);
        openExitAfterAuth = getIntent().getBooleanExtra(EXTRA_OPEN_EXIT_AFTER_AUTH, false);

        if (openExitAfterAuth) {
            subtitleView.setText(R.string.exit_kiosk_auth_subtitle);
        }

        bindDigitButton(R.id.keypad0, "0");
        bindDigitButton(R.id.keypad1, "1");
        bindDigitButton(R.id.keypad2, "2");
        bindDigitButton(R.id.keypad3, "3");
        bindDigitButton(R.id.keypad4, "4");
        bindDigitButton(R.id.keypad5, "5");
        bindDigitButton(R.id.keypad6, "6");
        bindDigitButton(R.id.keypad7, "7");
        bindDigitButton(R.id.keypad8, "8");
        bindDigitButton(R.id.keypad9, "9");

        findViewById(R.id.keypadClear).setOnClickListener(v -> passwordField.setText(""));
        findViewById(R.id.keypadDelete).setOnClickListener(v -> removeLastCharacter());
        findViewById(R.id.keypadConfirm).setOnClickListener(v -> validateAccess());
        findViewById(R.id.adminEnterButton).setOnClickListener(v -> validateAccess());
        findViewById(R.id.adminCancelButton).setOnClickListener(v -> finish());
    }

    private void bindDigitButton(int viewId, String digit) {
        findViewById(viewId).setOnClickListener(v -> {
            errorView.setText("");
            passwordField.append(digit);
        });
    }

    private void removeLastCharacter() {
        String current = passwordField.getText().toString();
        if (!TextUtils.isEmpty(current)) {
            passwordField.setText(current.substring(0, current.length() - 1));
            passwordField.setSelection(passwordField.getText().length());
        }
    }

    private void validateAccess() {
        String password = passwordField.getText().toString().trim();
        if (preferenceManager.validateAdminPassword(password)) {
            Class<?> destination = openExitAfterAuth
                    ? ExitKioskActivity.class
                    : AdminPanelActivity.class;
            startActivity(new Intent(this, destination));
            finish();
            return;
        }

        errorView.setText(R.string.error_incorrect_password);
        Toast.makeText(this, R.string.error_incorrect_password, Toast.LENGTH_SHORT).show();
        passwordField.setText("");
    }
}
