package com.hxkiosk;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.View;
import android.view.Window;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class ScreenCaptureHelper {

    private static volatile byte[] latestWindowJpeg;
    private static volatile long lastWindowAt;

    private ScreenCaptureHelper() {
    }

    @Nullable
    public static byte[] captureJpeg(@Nullable Activity activity) {
        long now = System.currentTimeMillis();
        if (latestWindowJpeg != null && now - lastWindowAt < 90L) {
            return latestWindowJpeg;
        }
        byte[] windowJpeg = captureWindowJpeg(activity);
        if (windowJpeg != null) {
            latestWindowJpeg = windowJpeg;
            lastWindowAt = now;
            return windowJpeg;
        }
        return latestWindowJpeg;
    }

    @Nullable
    static byte[] compressJpeg(@Nullable Bitmap bitmap, int maxEdge, int quality) {
        if (bitmap == null) {
            return null;
        }
        if (Math.max(bitmap.getWidth(), bitmap.getHeight()) > maxEdge) {
            float scale = maxEdge / (float) Math.max(bitmap.getWidth(), bitmap.getHeight());
            Bitmap scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    Math.max(2, Math.round(bitmap.getWidth() * scale)),
                    Math.max(2, Math.round(bitmap.getHeight() * scale)),
                    true
            );
            if (scaled != bitmap) {
                bitmap.recycle();
                bitmap = scaled;
            }
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
        bitmap.recycle();
        return outputStream.toByteArray();
    }

    @Nullable
    private static byte[] captureWindowJpeg(@Nullable Activity activity) {
        if (activity == null) {
            return null;
        }
        Window window = activity.getWindow();
        View rootView = window == null ? null : window.getDecorView().getRootView();
        if (rootView == null || rootView.getWidth() <= 0 || rootView.getHeight() <= 0) {
            return null;
        }

        Bitmap bitmap = Bitmap.createBitmap(rootView.getWidth(), rootView.getHeight(), Bitmap.Config.ARGB_8888);
        boolean copied = copyWithPixelCopy(window, bitmap);
        if (!copied) {
            Canvas canvas = new Canvas(bitmap);
            rootView.draw(canvas);
        }
        return compressJpeg(bitmap, 1280, 55);
    }

    static Bitmap cropLetterbox(Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int left = 0;
        int top = 0;
        int right = width - 1;
        int bottom = height - 1;
        while (left < right && isBlackColumn(source, left)) {
            left++;
        }
        while (right > left && isBlackColumn(source, right)) {
            right--;
        }
        while (top < bottom && isBlackRow(source, top)) {
            top++;
        }
        while (bottom > top && isBlackRow(source, bottom)) {
            bottom--;
        }
        int cropW = right - left + 1;
        int cropH = bottom - top + 1;
        if (cropW <= 8 || cropH <= 8 || (left < 4 && top < 4 && right > width - 5 && bottom > height - 5)) {
            return source;
        }
        return Bitmap.createBitmap(source, left, top, cropW, cropH);
    }

    private static boolean isBlackColumn(Bitmap bitmap, int x) {
        int height = bitmap.getHeight();
        int step = Math.max(1, height / 24);
        int dark = 0;
        int samples = 0;
        for (int y = 0; y < height; y += step) {
            samples++;
            int color = bitmap.getPixel(x, y);
            if (((color >> 16) & 0xff) + ((color >> 8) & 0xff) + (color & 0xff) < 30) {
                dark++;
            }
        }
        return samples > 0 && dark * 10 >= samples * 9;
    }

    private static boolean isBlackRow(Bitmap bitmap, int y) {
        int width = bitmap.getWidth();
        int step = Math.max(1, width / 24);
        int dark = 0;
        int samples = 0;
        for (int x = 0; x < width; x += step) {
            samples++;
            int color = bitmap.getPixel(x, y);
            if (((color >> 16) & 0xff) + ((color >> 8) & 0xff) + (color & 0xff) < 30) {
                dark++;
            }
        }
        return samples > 0 && dark * 10 >= samples * 9;
    }

    private static boolean copyWithPixelCopy(Window window, Bitmap bitmap) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> success = new AtomicReference<>(false);
        Handler handler = new Handler(Looper.getMainLooper());
        try {
            PixelCopy.request(window, bitmap, copyResult -> {
                success.set(copyResult == PixelCopy.SUCCESS);
                latch.countDown();
            }, handler);
            latch.await(250, TimeUnit.MILLISECONDS);
            return Boolean.TRUE.equals(success.get());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
