package com.hxkiosk;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.LinkedHashSet;
import java.util.Set;

public class PreferenceManager {

    public static final String MODE_LINK = "link";
    public static final String MODE_APPS = "apps";

    public static final String KEY_BLOCK_SETTINGS = "block_settings";
    public static final String KEY_BLOCK_NOTIFICATIONS = "block_notifications";
    public static final String KEY_BLOCK_BACK_BUTTON = "block_back_button";
    public static final String KEY_BLOCK_RECENT_BUTTON = "block_recent_button";
    public static final String KEY_PREVENT_KIOSK_EXIT = "prevent_kiosk_exit";
    public static final String KEY_AUTO_START_KIOSK = "auto_start_kiosk";
    public static final String KEY_SHOW_LOGO = "show_logo";
    public static final String KEY_REMOTE_ACCESS = "remote_access";
    public static final String KEY_KIOSK_SESSION_ACTIVE = "kiosk_session_active";
    private static final String KEY_KIOSK_LOCKS_ENABLED = "kiosk_locks_enabled_v2";

    private static final String PREFS_NAME = "hxkiosk_prefs";
    private static final String KEY_ADMIN_PASSWORD = "admin_password";
    private static final String KEY_KIOSK_MODE = "kiosk_mode";
    private static final String KEY_ALLOWED_URL = "allowed_url";
    private static final String KEY_ALLOWED_APPS = "allowed_apps";
    private static final String KEY_LAUNCHER_NAME = "launcher_name";
    private static final String KEY_PRIMARY_COLOR = "primary_color";
    private static final String KEY_GRID_COLUMNS = "grid_columns";

    private final SharedPreferences preferences;

    public PreferenceManager(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isSetupCompleted() {
        return !TextUtils.isEmpty(preferences.getString(KEY_ADMIN_PASSWORD, ""));
    }

    public void saveAdminPassword(String password) {
        // TODO: Encrypt or hash this value before shipping to production devices.
        preferences.edit().putString(KEY_ADMIN_PASSWORD, password).apply();
    }

    public boolean validateAdminPassword(String password) {
        return TextUtils.equals(preferences.getString(KEY_ADMIN_PASSWORD, ""), password);
    }

    public String getKioskMode() {
        return preferences.getString(KEY_KIOSK_MODE, MODE_LINK);
    }

    public void setKioskMode(String mode) {
        preferences.edit().putString(KEY_KIOSK_MODE, mode).apply();
    }

    public String getAllowedUrl() {
        return preferences.getString(KEY_ALLOWED_URL, "https://example.com");
    }

    public void setAllowedUrl(String url) {
        String safeUrl = TextUtils.isEmpty(url) ? "https://example.com" : url.trim();
        preferences.edit().putString(KEY_ALLOWED_URL, safeUrl).apply();
    }

    public Set<String> getAllowedApps() {
        Set<String> stored = preferences.getStringSet(KEY_ALLOWED_APPS, new LinkedHashSet<>());
        return new LinkedHashSet<>(stored);
    }

    public void setAllowedApps(Set<String> packages) {
        preferences.edit().putStringSet(KEY_ALLOWED_APPS, new LinkedHashSet<>(packages)).apply();
    }

    public boolean getBooleanConfig(String key, boolean defaultValue) {
        return preferences.getBoolean(key, defaultValue);
    }

    public void setBooleanConfig(String key, boolean value) {
        preferences.edit().putBoolean(key, value).apply();
    }

    public String getLauncherName() {
        return preferences.getString(KEY_LAUNCHER_NAME, "HX KIOSK");
    }

    public void setLauncherName(String name) {
        preferences.edit().putString(KEY_LAUNCHER_NAME, name).apply();
    }

    public String getPrimaryColor() {
        return preferences.getString(KEY_PRIMARY_COLOR, "blue");
    }

    public void setPrimaryColor(String color) {
        preferences.edit().putString(KEY_PRIMARY_COLOR, color).apply();
    }

    public boolean shouldShowLogo() {
        return preferences.getBoolean(KEY_SHOW_LOGO, true);
    }

    public void setShowLogo(boolean showLogo) {
        preferences.edit().putBoolean(KEY_SHOW_LOGO, showLogo).apply();
    }

    public int getGridColumns() {
        return preferences.getInt(KEY_GRID_COLUMNS, 4);
    }

    public void setGridColumns(int columns) {
        preferences.edit().putInt(KEY_GRID_COLUMNS, columns).apply();
    }

    public boolean isKioskSessionActive() {
        return preferences.getBoolean(KEY_KIOSK_SESSION_ACTIVE, true);
    }

    public void setKioskSessionActive(boolean active) {
        preferences.edit().putBoolean(KEY_KIOSK_SESSION_ACTIVE, active).apply();
    }

    public void enableDefaultKioskLocks() {
        if (preferences.getBoolean(KEY_KIOSK_LOCKS_ENABLED, false)) {
            return;
        }
        preferences.edit()
                .putBoolean(KEY_BLOCK_NOTIFICATIONS, true)
                .putBoolean(KEY_PREVENT_KIOSK_EXIT, true)
                .putBoolean(KEY_BLOCK_BACK_BUTTON, true)
                .putBoolean(KEY_BLOCK_RECENT_BUTTON, true)
                .putBoolean(KEY_BLOCK_SETTINGS, true)
                .putBoolean(KEY_KIOSK_LOCKS_ENABLED, true)
                .apply();
    }

    public void resetSettingsKeepPassword() {
        String password = preferences.getString(KEY_ADMIN_PASSWORD, "");
        preferences.edit().clear().apply();
        saveAdminPassword(password);
        setBooleanConfig(KEY_REMOTE_ACCESS, true);
        setKioskSessionActive(true);
    }
}
