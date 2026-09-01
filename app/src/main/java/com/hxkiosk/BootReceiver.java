package com.hxkiosk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        PreferenceManager preferenceManager = new PreferenceManager(context);
        if (preferenceManager.isSetupCompleted()
                && preferenceManager.getBooleanConfig(PreferenceManager.KEY_REMOTE_ACCESS, true)) {
            RemoteAccessService.start(context);
        }

        if (!preferenceManager.getBooleanConfig(PreferenceManager.KEY_AUTO_START_KIOSK, false)) {
            return;
        }

        Intent launchIntent = new Intent(context, SplashActivity.class);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(launchIntent);
    }
}
