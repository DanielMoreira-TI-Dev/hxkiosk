package com.hxkiosk;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Locale;
import java.util.Set;

public class AdminPanelActivity extends AppCompatActivity {

    private static final String[] COLOR_LABELS = {"Azul", "Cinza", "Marrom", "Azul escuro"};
    private static final String[] COLOR_VALUES = {"blue", "gray", "brown", "dark_blue"};
    private static final int REQUEST_POST_NOTIFICATIONS = 32;

    private PreferenceManager preferenceManager;
    private RadioGroup modeGroup;
    private EditText allowedUrlField;
    private TextView appsSummaryText;
    private MaterialSwitch blockSettingsSwitch;
    private MaterialSwitch blockNotificationsSwitch;
    private MaterialSwitch blockBackSwitch;
    private MaterialSwitch blockRecentSwitch;
    private MaterialSwitch preventExitSwitch;
    private MaterialSwitch autoStartSwitch;
    private MaterialSwitch remoteAccessSwitch;
    private EditText launcherNameField;
    private Spinner colorSpinner;
    private MaterialSwitch showLogoSwitch;
    private RadioGroup columnsGroup;
    private View modeLinkSection;
    private View modeAppsSection;
    private TextView restrictionNoteText;
    private TextView devicePermissionStatusText;
    private TextView remoteAddressText;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_panel);

        preferenceManager = new PreferenceManager(this);
        bindViews();
        setupSpinner();
        loadPreferences();
        setupActions();
    }

    private void bindViews() {
        modeGroup = findViewById(R.id.operationModeGroup);
        allowedUrlField = findViewById(R.id.allowedUrlField);
        appsSummaryText = findViewById(R.id.authorizedAppsSummaryText);
        blockSettingsSwitch = findViewById(R.id.blockSettingsSwitch);
        blockNotificationsSwitch = findViewById(R.id.blockNotificationsSwitch);
        blockBackSwitch = findViewById(R.id.blockBackSwitch);
        blockRecentSwitch = findViewById(R.id.blockRecentSwitch);
        preventExitSwitch = findViewById(R.id.preventExitSwitch);
        autoStartSwitch = findViewById(R.id.autoStartSwitch);
        remoteAccessSwitch = findViewById(R.id.remoteAccessSwitch);
        launcherNameField = findViewById(R.id.launcherNameField);
        colorSpinner = findViewById(R.id.primaryColorSpinner);
        showLogoSwitch = findViewById(R.id.showLogoSwitch);
        columnsGroup = findViewById(R.id.columnsGroup);
        modeLinkSection = findViewById(R.id.modeLinkSection);
        modeAppsSection = findViewById(R.id.modeAppsSection);
        restrictionNoteText = findViewById(R.id.restrictionNoteText);
        devicePermissionStatusText = findViewById(R.id.devicePermissionStatusText);
        remoteAddressText = findViewById(R.id.remoteAddressText);
    }

    private void setupSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                COLOR_LABELS
        );
        colorSpinner.setAdapter(adapter);
    }

    private void setupActions() {
        modeGroup.setOnCheckedChangeListener((group, checkedId) -> toggleModeSections());
        findViewById(R.id.saveLinkButton).setOnClickListener(v -> saveLinkOnly());
        findViewById(R.id.selectAppsButton).setOnClickListener(v ->
                startActivity(new Intent(this, AppSelectionActivity.class)));
        findViewById(R.id.changePasswordButton).setOnClickListener(v ->
                startActivity(new Intent(this, ChangePasswordActivity.class)));
        findViewById(R.id.exitKioskButton).setOnClickListener(v ->
                startActivity(new Intent(this, ExitKioskActivity.class)));
        findViewById(R.id.restoreSettingsButton).setOnClickListener(v -> confirmReset());
        findViewById(R.id.saveSettingsButton).setOnClickListener(v -> saveAllPreferences());
        findViewById(R.id.grantDevicePermissionsButton).setOnClickListener(v -> requestMissingDevicePermissions());
        findViewById(R.id.releaseAccessButton).setOnClickListener(v -> confirmReleaseAccess());
        findViewById(R.id.copyRemoteAddressButton).setOnClickListener(v -> copyRemoteAddress());
    }

    private void loadPreferences() {
        String kioskMode = preferenceManager.getKioskMode();
        modeGroup.check(PreferenceManager.MODE_APPS.equals(kioskMode)
                ? R.id.modeAppsRadio
                : R.id.modeLinkRadio);

        allowedUrlField.setText(preferenceManager.getAllowedUrl());
        blockSettingsSwitch.setChecked(preferenceManager.getBooleanConfig(PreferenceManager.KEY_BLOCK_SETTINGS, true));
        blockNotificationsSwitch.setChecked(preferenceManager.getBooleanConfig(PreferenceManager.KEY_BLOCK_NOTIFICATIONS, true));
        blockBackSwitch.setChecked(preferenceManager.getBooleanConfig(PreferenceManager.KEY_BLOCK_BACK_BUTTON, true));
        blockRecentSwitch.setChecked(preferenceManager.getBooleanConfig(PreferenceManager.KEY_BLOCK_RECENT_BUTTON, true));
        preventExitSwitch.setChecked(preferenceManager.getBooleanConfig(PreferenceManager.KEY_PREVENT_KIOSK_EXIT, true));
        autoStartSwitch.setChecked(preferenceManager.getBooleanConfig(PreferenceManager.KEY_AUTO_START_KIOSK, false));
        remoteAccessSwitch.setChecked(preferenceManager.getBooleanConfig(PreferenceManager.KEY_REMOTE_ACCESS, true));
        launcherNameField.setText(preferenceManager.getLauncherName());
        showLogoSwitch.setChecked(preferenceManager.shouldShowLogo());
        columnsGroup.check(resolveColumnsRadioId(preferenceManager.getGridColumns()));
        restrictionNoteText.setText(R.string.kiosk_restriction_note);

        String primaryColor = preferenceManager.getPrimaryColor();
        for (int i = 0; i < COLOR_VALUES.length; i++) {
            if (TextUtils.equals(COLOR_VALUES[i], primaryColor)) {
                colorSpinner.setSelection(i);
                break;
            }
        }

        updateAppsSummary();
        toggleModeSections();
        updateDevicePermissionStatus();
        updateRemoteAddress();
    }

    private void toggleModeSections() {
        boolean linkMode = modeGroup.getCheckedRadioButtonId() == R.id.modeLinkRadio;
        modeLinkSection.setVisibility(linkMode ? View.VISIBLE : View.GONE);
        modeAppsSection.setVisibility(linkMode ? View.GONE : View.VISIBLE);
    }

    private void saveLinkOnly() {
        String normalized = normalizeUrl(allowedUrlField.getText().toString());
        if (normalized == null) {
            allowedUrlField.setError(getString(R.string.error_invalid_url));
            return;
        }
        preferenceManager.setAllowedUrl(normalized);
        Toast.makeText(this, R.string.link_saved, Toast.LENGTH_SHORT).show();
    }

    private void saveAllPreferences() {
        boolean linkMode = modeGroup.getCheckedRadioButtonId() == R.id.modeLinkRadio;
        String normalized = normalizeUrl(allowedUrlField.getText().toString());
        if (normalized == null) {
            allowedUrlField.setError(getString(R.string.error_invalid_url));
            return;
        }

        String launcherName = launcherNameField.getText().toString().trim();
        if (TextUtils.isEmpty(launcherName)) {
            launcherName = getString(R.string.default_launcher_name);
        }

        preferenceManager.setKioskMode(linkMode ? PreferenceManager.MODE_LINK : PreferenceManager.MODE_APPS);
        preferenceManager.setAllowedUrl(normalized);
        preferenceManager.setBooleanConfig(PreferenceManager.KEY_BLOCK_SETTINGS, blockSettingsSwitch.isChecked());
        preferenceManager.setBooleanConfig(PreferenceManager.KEY_BLOCK_NOTIFICATIONS, blockNotificationsSwitch.isChecked());
        preferenceManager.setBooleanConfig(PreferenceManager.KEY_BLOCK_BACK_BUTTON, blockBackSwitch.isChecked());
        preferenceManager.setBooleanConfig(PreferenceManager.KEY_BLOCK_RECENT_BUTTON, blockRecentSwitch.isChecked());
        preferenceManager.setBooleanConfig(PreferenceManager.KEY_PREVENT_KIOSK_EXIT, preventExitSwitch.isChecked());
        preferenceManager.setBooleanConfig(PreferenceManager.KEY_AUTO_START_KIOSK, autoStartSwitch.isChecked());
        preferenceManager.setBooleanConfig(PreferenceManager.KEY_REMOTE_ACCESS, remoteAccessSwitch.isChecked());
        preferenceManager.setLauncherName(launcherName);
        preferenceManager.setPrimaryColor(COLOR_VALUES[colorSpinner.getSelectedItemPosition()]);
        preferenceManager.setShowLogo(showLogoSwitch.isChecked());
        preferenceManager.setGridColumns(resolveSelectedColumns());
        preferenceManager.setKioskSessionActive(true);
        KioskPolicyManager.applyDeviceOwnerPolicies(this, preferenceManager);
        if (HxKioskApp.get() != null) {
            HxKioskApp.get().syncRemoteAccessService();
        }
        maybeRequestNotificationPermission();
        updateDevicePermissionStatus();
        updateRemoteAddress();

        Toast.makeText(this, R.string.saved_successfully, Toast.LENGTH_SHORT).show();
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.restore_confirmation)
                .setPositiveButton(R.string.restore_settings, (dialog, which) -> {
                    preferenceManager.resetSettingsKeepPassword();
                    loadPreferences();
                    Toast.makeText(this, R.string.saved_successfully, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmReleaseAccess() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.release_device_access)
                .setMessage(R.string.release_device_access_message)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    preferenceManager.setKioskSessionActive(false);
                    boolean released = KioskPolicyManager.releaseManagedAccess(this);
                    Toast.makeText(
                            this,
                            released ? R.string.release_device_access_success : R.string.release_device_access_partial,
                            Toast.LENGTH_LONG
                    ).show();
                    updateDevicePermissionStatus();
                    KioskPolicyManager.openHomeSettings(this);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updateAppsSummary() {
        Set<String> allowedApps = preferenceManager.getAllowedApps();
        if (allowedApps.isEmpty()) {
            appsSummaryText.setText(R.string.apps_count_zero);
            return;
        }
        appsSummaryText.setText(String.format(Locale.getDefault(),
                getString(R.string.apps_count_format), allowedApps.size()));
    }

    private int resolveColumnsRadioId(int columns) {
        if (columns == 3) {
            return R.id.columns3Radio;
        }
        if (columns == 5) {
            return R.id.columns5Radio;
        }
        return R.id.columns4Radio;
    }

    private int resolveSelectedColumns() {
        int checkedId = columnsGroup.getCheckedRadioButtonId();
        if (checkedId == R.id.columns3Radio) {
            return 3;
        }
        if (checkedId == R.id.columns5Radio) {
            return 5;
        }
        return 4;
    }

    private String normalizeUrl(String rawUrl) {
        String candidate = rawUrl == null ? "" : rawUrl.trim();
        if (TextUtils.isEmpty(candidate)) {
            return "https://example.com";
        }
        if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
            return candidate;
        }
        return "https://" + candidate;
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAppsSummary();
        updateDevicePermissionStatus();
        updateRemoteAddress();
        KioskPolicyManager.applyDeviceOwnerPolicies(this, preferenceManager);
    }

    private void updateDevicePermissionStatus() {
        devicePermissionStatusText.setText(KioskPolicyManager.buildPermissionStatus(this));
    }

    private void updateRemoteAddress() {
        boolean enabled = preferenceManager.getBooleanConfig(PreferenceManager.KEY_REMOTE_ACCESS, true);
        String ip = LanAddressHelper.getIpv4Address(this);
        if (!enabled) {
            remoteAddressText.setText(R.string.remote_access_disabled);
            return;
        }
        if (TextUtils.isEmpty(ip)) {
            remoteAddressText.setText(R.string.remote_waiting_network);
            return;
        }
        remoteAddressText.setText(getString(
                R.string.remote_address_format,
                ip,
                RemoteAccessService.PORT
        ));
    }

    private void copyRemoteAddress() {
        String ip = LanAddressHelper.getIpv4Address(this);
        if (TextUtils.isEmpty(ip)) {
            Toast.makeText(this, R.string.remote_waiting_network, Toast.LENGTH_SHORT).show();
            return;
        }
        String address = "http://" + ip + ":" + RemoteAccessService.PORT + "/";
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("hxkiosk", address));
        }
        Toast.makeText(this, address, Toast.LENGTH_SHORT).show();
    }

    private void maybeRequestNotificationPermission() {
        if (!preferenceManager.getBooleanConfig(PreferenceManager.KEY_REMOTE_ACCESS, true)) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_POST_NOTIFICATIONS);
        }
    }

    private void requestMissingDevicePermissions() {
        if (!KioskPolicyManager.isAccessibilityEnabled(this)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.accessibility_title)
                    .setMessage(R.string.accessibility_message)
                    .setPositiveButton(R.string.open_permission_screen, (dialog, which) ->
                            KioskPolicyManager.requestAccessibility(this))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }

        if (!KioskPolicyManager.isNotificationListenerEnabled(this)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.notification_listener_title)
                    .setMessage(R.string.notification_listener_message)
                    .setPositiveButton(R.string.open_permission_screen, (dialog, which) ->
                            KioskPolicyManager.requestNotificationListener(this))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }

        if (!KioskPolicyManager.isHomeApp(this)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.launcher_role_title)
                    .setMessage(R.string.launcher_role_message)
                    .setPositiveButton(R.string.open_launcher_selection, (dialog, which) ->
                            KioskPolicyManager.requestHomeRole(this))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }

        maybeRequestNotificationPermission();
        KioskPolicyManager.applyDeviceOwnerPolicies(this, preferenceManager);
        updateDevicePermissionStatus();
        Toast.makeText(this, R.string.device_permissions_ready, Toast.LENGTH_SHORT).show();
    }
}
