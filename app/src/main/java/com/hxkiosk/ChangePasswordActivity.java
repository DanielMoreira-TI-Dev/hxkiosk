package com.hxkiosk;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class ChangePasswordActivity extends AppCompatActivity {

    private PreferenceManager preferenceManager;
    private EditText currentPasswordField;
    private EditText newPasswordField;
    private EditText confirmPasswordField;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        preferenceManager = new PreferenceManager(this);
        currentPasswordField = findViewById(R.id.currentPasswordField);
        newPasswordField = findViewById(R.id.newPasswordField);
        confirmPasswordField = findViewById(R.id.confirmNewPasswordField);

        findViewById(R.id.changePasswordSaveButton).setOnClickListener(v -> changePassword());
    }

    private void changePassword() {
        String current = currentPasswordField.getText().toString().trim();
        String newPassword = newPasswordField.getText().toString().trim();
        String confirmation = confirmPasswordField.getText().toString().trim();

        if (!preferenceManager.validateAdminPassword(current)) {
            currentPasswordField.setError(getString(R.string.error_incorrect_password));
            return;
        }

        if (TextUtils.isEmpty(newPassword)) {
            newPasswordField.setError(getString(R.string.error_password_required));
            return;
        }

        if (newPassword.length() < 4) {
            newPasswordField.setError(getString(R.string.error_password_length));
            return;
        }

        if (!TextUtils.equals(newPassword, confirmation)) {
            confirmPasswordField.setError(getString(R.string.error_password_mismatch));
            return;
        }

        preferenceManager.saveAdminPassword(newPassword);
        Toast.makeText(this, R.string.password_changed_successfully, Toast.LENGTH_SHORT).show();
        finish();
    }
}
