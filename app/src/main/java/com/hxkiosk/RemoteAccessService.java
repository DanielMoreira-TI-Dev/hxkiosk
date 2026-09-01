package com.hxkiosk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class RemoteAccessService extends Service {

    public static final int PORT = 8787;
    private static final String CHANNEL_ID = "hxkiosk_remote";
    private static final int NOTIFICATION_ID = 8787;

    private RemoteHttpServer httpServer;

    public static void start(Context context) {
        try {
            Intent intent = new Intent(context, RemoteAccessService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (RuntimeException ignored) {
            // Sem atividade visivel o Android 14+ recusa o servico.
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, RemoteAccessService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        if (httpServer == null) {
            httpServer = new RemoteHttpServer(this, PORT);
        }
        httpServer.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (httpServer == null) {
            httpServer = new RemoteHttpServer(this, PORT);
        }
        httpServer.start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.remote_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.remote_channel_description));
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent panelIntent = new Intent(this, AdminPanelActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                panelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        String ip = LanAddressHelper.getIpv4Address(this);
        String address = ip.isEmpty()
                ? getString(R.string.remote_waiting_network)
                : "http://" + ip + ":" + PORT;
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_status_wifi)
                .setContentTitle(getString(R.string.remote_notification_title))
                .setContentText(address)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build();
    }
}
