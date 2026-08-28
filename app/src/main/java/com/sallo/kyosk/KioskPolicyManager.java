package com.sallo.kyosk;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.UserManager;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

public final class KioskPolicyManager {

    private KioskPolicyManager() {
    }

    @NonNull
    public static ComponentName getAdminComponent(@NonNull Context context) {
        return new ComponentName(context, MyDeviceAdminReceiver.class);
    }

    @Nullable
    private static DevicePolicyManager getDevicePolicyManager(@NonNull Context context) {
        return (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    public static boolean isDeviceAdminActive(@NonNull Context context) {
        DevicePolicyManager manager = getDevicePolicyManager(context);
        return manager != null && manager.isAdminActive(getAdminComponent(context));
    }

    public static boolean isDeviceOwner(@NonNull Context context) {
        DevicePolicyManager manager = getDevicePolicyManager(context);
        return manager != null && manager.isDeviceOwnerApp(context.getPackageName());
    }

    public static boolean isHomeApp(@NonNull Context context) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolveInfo = context.getPackageManager()
                .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return resolveInfo != null
                && resolveInfo.activityInfo != null
                && context.getPackageName().equals(resolveInfo.activityInfo.packageName);
    }

    public static void requestDeviceAdmin(@NonNull Activity activity) {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getAdminComponent(activity));
        intent.putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                activity.getString(R.string.device_admin_request_explanation)
        );
        activity.startActivity(intent);
    }

    public static void requestHomeRole(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = activity.getSystemService(RoleManager.class);
            if (roleManager != null
                    && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)
                    && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                activity.startActivity(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME));
                return;
            }
        }

        openHomeSettings(activity);
    }

    public static void openHomeSettings(@NonNull Activity activity) {
        Intent homeSettingsIntent = new Intent(Settings.ACTION_HOME_SETTINGS);
        if (homeSettingsIntent.resolveActivity(activity.getPackageManager()) != null) {
            activity.startActivity(homeSettingsIntent);
            return;
        }

        Intent chooserIntent = new Intent(Intent.ACTION_MAIN);
        chooserIntent.addCategory(Intent.CATEGORY_HOME);
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(chooserIntent);
    }

    public static boolean requiresManagedKiosk(@NonNull PreferenceManager preferenceManager) {
        return preferenceManager.getBooleanConfig(PreferenceManager.KEY_BLOCK_SETTINGS, false)
                || preferenceManager.getBooleanConfig(PreferenceManager.KEY_BLOCK_NOTIFICATIONS, false)
                || preferenceManager.getBooleanConfig(PreferenceManager.KEY_BLOCK_RECENT_BUTTON, false)
                || preferenceManager.getBooleanConfig(PreferenceManager.KEY_PREVENT_KIOSK_EXIT, false);
    }

    public static boolean shouldStartLockTask(@NonNull PreferenceManager preferenceManager) {
        return requiresManagedKiosk(preferenceManager);
    }

    @NonNull
    private static String[] getLockTaskPackages(
            @NonNull Context context,
            @NonNull PreferenceManager preferenceManager
    ) {
        Set<String> packages = new LinkedHashSet<>();
        packages.add(context.getPackageName());
        if (PreferenceManager.MODE_APPS.equals(preferenceManager.getKioskMode())) {
            packages.addAll(preferenceManager.getAllowedApps());
        }
        return packages.toArray(new String[0]);
    }

    public static void applyDeviceOwnerPolicies(
            @NonNull Context context,
            @NonNull PreferenceManager preferenceManager
    ) {
        DevicePolicyManager manager = getDevicePolicyManager(context);
        if (manager == null || !isDeviceOwner(context)) {
            return;
        }

        ComponentName adminComponent = getAdminComponent(context);
        boolean managedKiosk = requiresManagedKiosk(preferenceManager);

        manager.setLockTaskPackages(
                adminComponent,
                managedKiosk ? getLockTaskPackages(context, preferenceManager) : new String[0]
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            int features = 0;
            if (!preferenceManager.getBooleanConfig(PreferenceManager.KEY_BLOCK_NOTIFICATIONS, false)) {
                features |= DevicePolicyManager.LOCK_TASK_FEATURE_HOME;
                features |= DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS;
            }
            if (!preferenceManager.getBooleanConfig(PreferenceManager.KEY_PREVENT_KIOSK_EXIT, false)) {
                features |= DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS;
            }
            manager.setLockTaskFeatures(adminComponent, features);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean disableStatusBar = preferenceManager.getBooleanConfig(
                    PreferenceManager.KEY_BLOCK_NOTIFICATIONS,
                    false
            );
            manager.setStatusBarDisabled(adminComponent, disableStatusBar);
        }

        if (preferenceManager.getBooleanConfig(PreferenceManager.KEY_PREVENT_KIOSK_EXIT, false)) {
            manager.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT);
        } else {
            manager.clearUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT);
        }
    }

    public static void applyLockTaskMode(
            @NonNull Activity activity,
            @NonNull PreferenceManager preferenceManager
    ) {
        if (PreferenceManager.MODE_APPS.equals(preferenceManager.getKioskMode())
                && !isDeviceOwner(activity)) {
            stopLockTaskIfNeeded(activity);
            return;
        }

        if (shouldStartLockTask(preferenceManager)) {
            if (!isInLockTaskMode(activity)) {
                try {
                    activity.startLockTask();
                } catch (IllegalArgumentException | SecurityException ignored) {
                    // The device is not provisioned for full kiosk mode yet.
                }
            }
            return;
        }

        stopLockTaskIfNeeded(activity);
    }

    public static void stopLockTaskIfNeeded(@NonNull Activity activity) {
        if (!isInLockTaskMode(activity)) {
            return;
        }
        try {
            activity.stopLockTask();
        } catch (IllegalArgumentException ignored) {
            // Ignore if the activity is no longer in lock task mode.
        }
    }

    public static boolean isInLockTaskMode(@NonNull Context context) {
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return activityManager.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE;
        }
        return activityManager.isInLockTaskMode();
    }

    @NonNull
    public static String buildPermissionStatus(@NonNull Context context) {
        return context.getString(
                R.string.device_permission_status_format,
                enabledDisabled(context, isDeviceAdminActive(context)),
                enabledDisabled(context, isDeviceOwner(context)),
                enabledDisabled(context, isHomeApp(context))
        );
    }

    @NonNull
    public static String getDeviceOwnerProvisioningCommand(@NonNull Context context) {
        return "adb shell dpm set-device-owner "
                + context.getPackageName()
                + "/."
                + MyDeviceAdminReceiver.class.getSimpleName();
    }

    @NonNull
    private static String enabledDisabled(@NonNull Context context, boolean enabled) {
        return context.getString(enabled
                ? R.string.permission_status_enabled
                : R.string.permission_status_disabled);
    }
}
