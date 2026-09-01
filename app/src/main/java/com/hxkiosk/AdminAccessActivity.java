package com.hxkiosk;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

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
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        setContentView(R.layout.activity_admin_access);
        showSystemBars();
        KioskPolicyManager.stopLockTaskIfNeeded(this);

        preferenceManager = new PreferenceManager(this);
        passwordField = findViewById(R.id.adminPasswordField);
        errorView = findViewById(R.id.adminAccessError);
        subtitleView = findViewById(R.id.adminAccessSubtitle);
        openExitAfterAuth = getIntent().getBooleanExtra(EXTRA_OPEN_EXIT_AFTER_AUTH, false);

        if (openExitAfterAuth) {
            subtitleView.setText(R.string.exit_kiosk_auth_subtitle);
        }

        passwordField.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                validateAccess();
                return true;
            }
            return false;
        });
        findViewById(R.id.adminEnterButton).setOnClickListener(v -> validateAccess());
        findViewById(R.id.adminCancelButton).setOnClickListener(v -> finish());

        passwordField.requestFocus();
        passwordField.post(() -> RemoteInput.showIme(this));
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
        passwordField.requestFocus();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            showSystemBars();
        }
    }

    private void showSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            }
            return;
        }
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }
}
