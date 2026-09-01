package com.hxkiosk;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class KioskNotificationListener extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) {
            return;
        }
        PreferenceManager preferenceManager = new PreferenceManager(this);
        if (!preferenceManager.isKioskSessionActive()) {
            return;
        }
        if (!preferenceManager.getBooleanConfig(PreferenceManager.KEY_BLOCK_NOTIFICATIONS, true)) {
            return;
        }
        if (getPackageName().equals(sbn.getPackageName())) {
            return;
        }
        try {
            cancelNotification(sbn.getKey());
        } catch (SecurityException ignored) {
            // Listener still waiting for user grant.
        }
    }
}
