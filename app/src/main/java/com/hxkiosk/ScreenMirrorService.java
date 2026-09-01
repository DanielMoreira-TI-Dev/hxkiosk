package com.hxkiosk;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class ScreenMirrorService extends Service {

    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    private static final String CHANNEL_ID = "hxkiosk_mirror";
    private static final int NOTIFICATION_ID = 8788;

    private static volatile boolean capturing;
    private static volatile byte[] latestJpeg;
    private long lastEncodeAt;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread imageThread;

    public static void start(Context context, int resultCode, Intent resultData) {
        Intent intent = new Intent(context, ScreenMirrorService.class);
        intent.putExtra(EXTRA_RESULT_CODE, resultCode);
        intent.putExtra(EXTRA_RESULT_DATA, resultData);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, ScreenMirrorService.class));
    }

    public static boolean isCapturing() {
        return capturing;
    }

    @Nullable
    public static byte[] getLatestJpeg() {
        return latestJpeg;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_status_wifi)
                .setContentTitle(getString(R.string.remote_mirror_title))
                .setContentText(getString(R.string.remote_mirror_text))
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        if (intent == null) {
            return START_NOT_STICKY;
        }
        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
        Intent resultData;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        } else {
            resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        }
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startProjection(resultCode, resultData);
        return START_STICKY;
    }

    private void startProjection(int resultCode, Intent resultData) {
        stopProjection();
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (manager == null) {
            stopSelf();
            return;
        }
        mediaProjection = manager.getMediaProjection(resultCode, resultData);
        if (mediaProjection == null) {
            stopSelf();
            return;
        }
        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                capturing = false;
                stopSelf();
            }
        }, new Handler(getMainLooper()));

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager != null) {
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
        } else {
            metrics = getResources().getDisplayMetrics();
        }
        int rawWidth = Math.max(metrics.widthPixels, 1);
        int rawHeight = Math.max(metrics.heightPixels, 1);
        final int width = rawWidth;
        final int height = rawHeight;
        final int density = Math.max(metrics.densityDpi, DisplayMetrics.DENSITY_DEFAULT);

        imageThread = new HandlerThread("hxkiosk-mirror");
        imageThread.start();
        Handler imageHandler = new Handler(imageThread.getLooper());
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
        imageReader.setOnImageAvailableListener(reader -> {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image == null) {
                    return;
                }
                long now = System.currentTimeMillis();
                if (now - lastEncodeAt < 80L) {
                    return;
                }
                byte[] jpeg = imageToJpeg(image);
                if (jpeg != null && jpeg.length > 100) {
                    latestJpeg = jpeg;
                    lastEncodeAt = now;
                }
            } catch (Exception ignored) {
            } finally {
                if (image != null) {
                    image.close();
                }
            }
        }, imageHandler);

        virtualDisplay = mediaProjection.createVirtualDisplay(
                "hxkiosk-mirror",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                imageHandler
        );
        capturing = true;
    }

    @Nullable
    private byte[] imageToJpeg(Image image) {
        Bitmap bitmap = imageToBitmap(image);
        if (bitmap == null || isMostlyBlack(bitmap)) {
            if (bitmap != null) {
                bitmap.recycle();
            }
            return null;
        }
        Bitmap cropped = ScreenCaptureHelper.cropLetterbox(bitmap);
        if (cropped != bitmap) {
            bitmap.recycle();
            bitmap = cropped;
        }
        int maxEdge = 960;
        if (Math.max(bitmap.getWidth(), bitmap.getHeight()) > maxEdge) {
            float scale = maxEdge / (float) Math.max(bitmap.getWidth(), bitmap.getHeight());
            int scaledW = Math.max(2, Math.round(bitmap.getWidth() * scale));
            int scaledH = Math.max(2, Math.round(bitmap.getHeight() * scale));
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true);
            bitmap.recycle();
            bitmap = scaled;
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream);
        bitmap.recycle();
        return outputStream.toByteArray();
    }

    @Nullable
    private Bitmap imageToBitmap(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        buffer.rewind();
        byte[] packed = new byte[width * height * 4];
        if (pixelStride == 4 && rowStride == width * 4 && buffer.remaining() >= packed.length) {
            buffer.get(packed, 0, packed.length);
        } else {
            byte[] row = new byte[rowStride];
            for (int y = 0; y < height; y++) {
                int toRead = Math.min(rowStride, buffer.remaining());
                if (toRead <= 0) {
                    break;
                }
                buffer.get(row, 0, toRead);
                int dest = y * width * 4;
                if (pixelStride == 4) {
                    System.arraycopy(row, 0, packed, dest, width * 4);
                } else {
                    for (int x = 0; x < width; x++) {
                        int src = x * pixelStride;
                        packed[dest + x * 4] = row[src];
                        packed[dest + x * 4 + 1] = row[src + 1];
                        packed[dest + x * 4 + 2] = row[src + 2];
                        packed[dest + x * 4 + 3] = pixelStride > 3 ? row[src + 3] : (byte) 0xff;
                    }
                }
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(packed));
        return bitmap;
    }

    private static boolean isMostlyBlack(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int samples = 0;
        int lit = 0;
        int stepX = Math.max(1, width / 16);
        int stepY = Math.max(1, height / 16);
        for (int y = height / 6; y < height - height / 6; y += stepY) {
            for (int x = width / 8; x < width - width / 8; x += stepX) {
                int color = bitmap.getPixel(x, y);
                int red = (color >> 16) & 0xff;
                int green = (color >> 8) & 0xff;
                int blue = color & 0xff;
                samples++;
                if (red + green + blue > 48) {
                    lit++;
                }
            }
        }
        return samples > 0 && lit * 8 < samples;
    }

    private void stopProjection() {
        capturing = false;
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        if (imageThread != null) {
            imageThread.quitSafely();
            imageThread = null;
        }
        latestJpeg = null;
    }

    @Override
    public void onDestroy() {
        stopProjection();
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
                getString(R.string.remote_mirror_title),
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }
}
