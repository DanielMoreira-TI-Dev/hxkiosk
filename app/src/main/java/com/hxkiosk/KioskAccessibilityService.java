package com.hxkiosk;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class KioskAccessibilityService extends AccessibilityService {

    private static final String TAG = "HxKioskA11y";
    private static WeakReference<KioskAccessibilityService> instance = new WeakReference<>(null);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PreferenceManager preferenceManager;
    private long lastHomeActionAt;
    private long lastShadeDismissAt;
    private long lastBringToFrontAt;
    private final Runnable kioskWatchdog = new Runnable() {
        @Override
        public void run() {
            enforceKioskWindows();
            handler.postDelayed(this, 280L);
        }
    };

    public static boolean tapNormalized(float normalizedX, float normalizedY) {
        return gestureNormalized(normalizedX, normalizedY, normalizedX, normalizedY, 50);
    }

    public static boolean performNav(String action) {
        if ("keyboard".equals(action)) {
            return true;
        }
        int globalAction;
        switch (action) {
            case "back":
                globalAction = GLOBAL_ACTION_BACK;
                break;
            case "home":
                globalAction = GLOBAL_ACTION_HOME;
                break;
            case "recents":
                globalAction = GLOBAL_ACTION_RECENTS;
                break;
            case "notifications":
                globalAction = GLOBAL_ACTION_NOTIFICATIONS;
                break;
            default:
                return false;
        }
        Activity activity = HxKioskApp.get() == null ? null : HxKioskApp.get().getResumedActivity();
        if (activity instanceof MainKioskActivity) {
            ((MainKioskActivity) activity).injectRemoteNav(action);
            PreferenceManager prefs = new PreferenceManager(activity);
            boolean stayInKiosk = prefs.getBooleanConfig(PreferenceManager.KEY_PREVENT_KIOSK_EXIT, true)
                    || prefs.getBooleanConfig(PreferenceManager.KEY_BLOCK_BACK_BUTTON, true)
                    || prefs.getBooleanConfig(PreferenceManager.KEY_BLOCK_RECENT_BUTTON, true);
            if ("back".equals(action) || (stayInKiosk && !"notifications".equals(action))) {
                return true;
            }
        }
        KioskAccessibilityService service = instance.get();
        boolean globalOk = false;
        if (service != null) {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean ok = new AtomicBoolean(false);
            service.handler.post(() -> {
                ok.set(service.performGlobalAction(globalAction));
                latch.countDown();
            });
            try {
                globalOk = latch.await(500, TimeUnit.MILLISECONDS) && ok.get();
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return true;
    }

    @Nullable
    public static byte[] captureDisplayJpeg() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null;
        }
        KioskAccessibilityService service = instance.get();
        if (service == null) {
            return null;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<byte[]> jpeg = new AtomicReference<>();
        try {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, command -> service.handler.post(command),
                    new TakeScreenshotCallback() {
                        @Override
                        public void onSuccess(@NonNull ScreenshotResult screenshot) {
                            HardwareBuffer buffer = screenshot.getHardwareBuffer();
                            try {
                                Bitmap hardware = Bitmap.wrapHardwareBuffer(buffer, screenshot.getColorSpace());
                                if (hardware == null) {
                                    return;
                                }
                                Bitmap software = hardware.copy(Bitmap.Config.ARGB_8888, false);
                                if (software != hardware) {
                                    hardware.recycle();
                                }
                                jpeg.set(ScreenCaptureHelper.compressJpeg(software, 1280, 55));
                            } catch (RuntimeException exception) {
                                Log.w(TAG, "Falha ao copiar screenshot da tela", exception);
                            } finally {
                                buffer.close();
                                latch.countDown();
                            }
                        }

                        @Override
                        public void onFailure(int errorCode) {
                            Log.w(TAG, "takeScreenshot falhou: " + errorCode);
                            latch.countDown();
                        }
                    });
            latch.await(700, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException exception) {
            Log.w(TAG, "takeScreenshot indisponivel", exception);
        }
        return jpeg.get();
    }

    public static float[] displaySize(@Nullable Context context) {
        KioskAccessibilityService service = instance.get();
        if (service != null) {
            return service.realScreenSize();
        }
        if (context == null) {
            return new float[] { 1f, 1f };
        }
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect bounds = windowManager.getCurrentWindowMetrics().getBounds();
            return new float[] { Math.max(1, bounds.width()), Math.max(1, bounds.height()) };
        }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return new float[] { Math.max(1, metrics.widthPixels), Math.max(1, metrics.heightPixels) };
    }

    public static boolean tapPixels(float x, float y) {
        return gesturePixels(x, y, x + 3f, y, 120, true);
    }

    public static boolean swipePixels(float startX, float startY, float endX, float endY, long durationMs) {
        return gesturePixels(startX, startY, endX, endY, Math.max(80L, durationMs), false);
    }

    public static boolean openNotificationShade() {
        KioskAccessibilityService service = instance.get();
        if (service == null) {
            return false;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean ok = new AtomicBoolean(false);
        service.handler.post(() -> {
            ok.set(service.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS));
            latch.countDown();
        });
        try {
            return latch.await(400, TimeUnit.MILLISECONDS) && ok.get();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static boolean swipeNormalized(
            float startX,
            float startY,
            float endX,
            float endY,
            long durationMs
    ) {
        return gestureNormalized(startX, startY, endX, endY, Math.max(80L, durationMs));
    }

    public static boolean typeKey(String key, String code) {
        KioskAccessibilityService service = instance.get();
        if (service == null) {
            return false;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean ok = new AtomicBoolean(false);
        service.handler.post(() -> {
            ok.set(service.typeKeyOnFocused(key, code));
            latch.countDown();
        });
        try {
            return latch.await(400, TimeUnit.MILLISECONDS) && ok.get();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean typeKeyOnFocused(String key, String code) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return false;
        }
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null) {
            focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        }
        if (focused == null) {
            root.recycle();
            return false;
        }
        try {
            if ("Enter".equals(key) || "NumpadEnter".equals(code) || "Enter".equals(code)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    return focused.performAction(
                            AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId()
                    );
                }
                return focused.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            if ("Escape".equals(key) || "Escape".equals(code)) {
                return performGlobalAction(GLOBAL_ACTION_BACK);
            }
            if ("Tab".equals(key) || "Tab".equals(code)) {
                return focused.performAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)
                        || focused.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            CharSequence current = focused.getText();
            String text = current == null ? "" : current.toString();
            if ("Backspace".equals(key) || "Backspace".equals(code)) {
                if (text.isEmpty()) {
                    return false;
                }
                text = text.substring(0, text.length() - 1);
            } else if (key.length() == 1) {
                text = text + key;
            } else {
                return false;
            }
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        } finally {
            focused.recycle();
            root.recycle();
        }
    }

    private static boolean gestureNormalized(
            float startX,
            float startY,
            float endX,
            float endY,
            long durationMs
    ) {
        KioskAccessibilityService service = instance.get();
        if (service == null) {
            Log.w(TAG, "Gesto remoto ignorado: acessibilidade desligada");
            return false;
        }
        float[] size = service.realScreenSize();
        float width = size[0];
        float height = size[1];
        float fromX = clamp(startX) * (width - 1f);
        float fromY = clamp(startY) * (height - 1f);
        float toX = clamp(endX) * (width - 1f);
        float toY = clamp(endY) * (height - 1f);
        boolean tap = Math.hypot(toX - fromX, toY - fromY) < 8f;
        return gesturePixels(fromX, fromY, toX, toY, durationMs, tap);
    }

    private static boolean gesturePixels(
            float fromX,
            float fromY,
            float toX,
            float toY,
            long durationMs,
            boolean alsoClick
    ) {
        KioskAccessibilityService service = instance.get();
        if (service == null) {
            Log.w(TAG, "Gesto remoto ignorado: acessibilidade desligada");
            return false;
        }
        float[] size = service.realScreenSize();
        fromX = Math.max(1f, Math.min(size[0] - 2f, fromX));
        fromY = Math.max(1f, Math.min(size[1] - 2f, fromY));
        toX = Math.max(1f, Math.min(size[0] - 2f, toX));
        toY = Math.max(1f, Math.min(size[1] - 2f, toY));
        if (Math.hypot(toX - fromX, toY - fromY) < 2f) {
            toX = Math.min(size[0] - 2f, fromX + 3f);
        }
        Path path = new Path();
        path.moveTo(fromX, fromY);
        path.lineTo(toX, toY);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, Math.max(80L, durationMs)))
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean accepted = new AtomicBoolean(false);
        final int tapX = Math.round(fromX);
        final int tapY = Math.round(fromY);
        service.handler.post(() -> {
            boolean queued = service.dispatchGesture(gesture, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    accepted.set(true);
                    latch.countDown();
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    Log.w(TAG, "Gesto remoto cancelado");
                    latch.countDown();
                }
            }, null);
            if (!queued) {
                Log.w(TAG, "dispatchGesture recusou o toque remoto");
                if (alsoClick) {
                    service.clickNodeAt(tapX, tapY);
                }
                latch.countDown();
            }
        });
        try {
            boolean finished = latch.await(800, TimeUnit.MILLISECONDS);
            if ((!finished || !accepted.get()) && alsoClick) {
                service.handler.post(() -> service.clickNodeAt(tapX, tapY));
            }
            return finished && accepted.get();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void clickNodeAt(int x, int y) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return;
        }
        AccessibilityNodeInfo hit = findClickableNode(root, x, y);
        if (hit != null) {
            hit.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            hit.recycle();
        }
        root.recycle();
    }

    private AccessibilityNodeInfo findClickableNode(AccessibilityNodeInfo node, int x, int y) {
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (!bounds.contains(x, y)) {
            return null;
        }
        AccessibilityNodeInfo best = node.isClickable() ? AccessibilityNodeInfo.obtain(node) : null;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            AccessibilityNodeInfo hit = findClickableNode(child, x, y);
            child.recycle();
            if (hit != null) {
                if (best != null) {
                    best.recycle();
                }
                best = hit;
            }
        }
        return best;
    }

    private float[] realScreenSize() {
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect bounds = windowManager.getCurrentWindowMetrics().getBounds();
            return new float[] { Math.max(1, bounds.width()), Math.max(1, bounds.height()) };
        }
        DisplayMetrics metrics = new DisplayMetrics();
        if (windowManager != null) {
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
        } else {
            metrics = getResources().getDisplayMetrics();
        }
        return new float[] {
                Math.max(1, metrics.widthPixels),
                Math.max(1, metrics.heightPixels)
        };
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = new WeakReference<>(this);
        preferenceManager = new PreferenceManager(this);
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                    | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                    | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            info.eventTypes |= AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    | AccessibilityEvent.TYPE_WINDOWS_CHANGED
                    | AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
            info.notificationTimeout = 0;
            setServiceInfo(info);
        }
        handler.removeCallbacks(kioskWatchdog);
        handler.post(kioskWatchdog);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(kioskWatchdog);
        if (instance.get() == this) {
            instance = new WeakReference<>(null);
        }
        super.onDestroy();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event == null || preferenceManager == null || event.getAction() != KeyEvent.ACTION_DOWN) {
            return super.onKeyEvent(event);
        }
        if (!preferenceManager.isKioskSessionActive()) {
            return super.onKeyEvent(event);
        }
        int keyCode = event.getKeyCode();
        boolean blockBack = preferenceManager.getBooleanConfig(PreferenceManager.KEY_BLOCK_BACK_BUTTON, true)
                || preferenceManager.getBooleanConfig(PreferenceManager.KEY_PREVENT_KIOSK_EXIT, true);
        boolean blockRecent = preferenceManager.getBooleanConfig(PreferenceManager.KEY_BLOCK_RECENT_BUTTON, true);
        boolean preventExit = preferenceManager.getBooleanConfig(PreferenceManager.KEY_PREVENT_KIOSK_EXIT, true);
        if (blockBack && keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        if (preventExit && (keyCode == KeyEvent.KEYCODE_HOME || keyCode == KeyEvent.KEYCODE_APP_SWITCH)) {
            KioskPolicyManager.bringKioskToFront(this);
            return true;
        }
        if (blockRecent && keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
            return true;
        }
        return super.onKeyEvent(event);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || preferenceManager == null) {
            return;
        }
        if (!preferenceManager.isKioskSessionActive()) {
            return;
        }

        CharSequence packageSequence = event.getPackageName();
        if (packageSequence == null) {
            return;
        }

        String packageName = packageSequence.toString();
        String className = event.getClassName() == null ? "" : event.getClassName().toString();
        boolean blockNotifications = preferenceManager.getBooleanConfig(
                PreferenceManager.KEY_BLOCK_NOTIFICATIONS,
                true
        );
        boolean blockRecent = preferenceManager.getBooleanConfig(
                PreferenceManager.KEY_BLOCK_RECENT_BUTTON,
                true
        );
        boolean blockSettings = preferenceManager.getBooleanConfig(
                PreferenceManager.KEY_BLOCK_SETTINGS,
                true
        );
        boolean preventExit = preferenceManager.getBooleanConfig(
                PreferenceManager.KEY_PREVENT_KIOSK_EXIT,
                true
        );

        if (blockNotifications && isNotificationShade(packageName, className, event)) {
            dismissNotificationShade();
            return;
        }

        if (blockRecent && isRecentsUi(packageName, className)) {
            goHome();
            return;
        }

        if (blockSettings && isSettingsUi(packageName, className)) {
            KioskPolicyManager.bringKioskToFront(this);
            return;
        }

        if (preventExit && shouldReturnToKiosk(packageName)) {
            bringKioskToFrontThrottled();
        }
    }

    @Override
    public void onInterrupt() {
    }

    private boolean shouldReturnToKiosk(String packageName) {
        if (getPackageName().equals(packageName)) {
            return false;
        }
        if (isSystemUiPackage(packageName) || isInputMethodPackage(packageName)) {
            return false;
        }
        if (PreferenceManager.MODE_APPS.equals(preferenceManager.getKioskMode())) {
            Set<String> allowedApps = preferenceManager.getAllowedApps();
            if (allowedApps.contains(packageName)) {
                return false;
            }
        }
        return true;
    }

    private boolean isNotificationShade(String packageName, String className, AccessibilityEvent event) {
        if (!isSystemUiPackage(packageName)) {
            return false;
        }
        String normalized = className.toLowerCase(Locale.ROOT);
        return normalized.contains("notification")
                || normalized.contains("statusbar")
                || normalized.contains("status_bar")
                || normalized.contains("shade")
                || normalized.contains("panelview")
                || normalized.contains("expanded")
                || normalized.contains("controlcenter")
                || normalized.contains("control_center")
                || normalized.contains("quicksettings")
                || normalized.contains("qscontainer")
                || event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
    }

    private boolean isRecentsUi(String packageName, String className) {
        String normalized = (packageName + " " + className).toLowerCase(Locale.ROOT);
        return normalized.contains("recents")
                || normalized.contains("overview")
                || normalized.contains("launcher3.quickstep")
                || normalized.contains("quickstep")
                || className.contains("RecentsActivity")
                || className.contains("OverviewActivity");
    }

    private boolean isSettingsUi(String packageName, String className) {
        String normalized = packageName.toLowerCase(Locale.ROOT);
        return normalized.contains("settings")
                || className.toLowerCase(Locale.ROOT).contains("settings");
    }

    private void enforceKioskWindows() {
        if (preferenceManager == null || !preferenceManager.isKioskSessionActive()) {
            return;
        }
        boolean blockNotifications = preferenceManager.getBooleanConfig(
                PreferenceManager.KEY_BLOCK_NOTIFICATIONS,
                true
        );
        boolean preventExit = preferenceManager.getBooleanConfig(
                PreferenceManager.KEY_PREVENT_KIOSK_EXIT,
                true
        );
        if (!blockNotifications && !preventExit) {
            return;
        }

        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null || windows.isEmpty()) {
            return;
        }
        try {
            for (AccessibilityWindowInfo window : windows) {
                if (window == null) {
                    continue;
                }
                if (blockNotifications && isSystemShadeWindow(window)) {
                    dismissNotificationShade();
                    return;
                }
                if (preventExit && window.isActive()) {
                    AccessibilityNodeInfo root = window.getRoot();
                    if (root == null) {
                        continue;
                    }
                    try {
                        CharSequence pkg = root.getPackageName();
                        if (pkg != null && shouldReturnToKiosk(pkg.toString())) {
                            bringKioskToFrontThrottled();
                            return;
                        }
                    } finally {
                        root.recycle();
                    }
                }
            }
        } finally {
            for (AccessibilityWindowInfo window : windows) {
                if (window != null) {
                    window.recycle();
                }
            }
        }
    }

    private boolean isSystemShadeWindow(AccessibilityWindowInfo window) {
        if (window.getType() != AccessibilityWindowInfo.TYPE_SYSTEM) {
            return false;
        }
        CharSequence title = window.getTitle();
        String titleText = title == null ? "" : title.toString().toLowerCase(Locale.ROOT);
        if (titleText.contains("notification")
                || titleText.contains("shade")
                || titleText.contains("control")
                || titleText.contains("status")
                || titleText.contains("quick")) {
            return true;
        }
        Rect bounds = new Rect();
        window.getBoundsInScreen(bounds);
        float[] size = realScreenSize();
        boolean fromTop = bounds.top <= size[1] * 0.08f;
        boolean tallEnough = bounds.height() > size[1] * 0.4f;
        boolean fromSide = bounds.left > size[0] * 0.35f && bounds.height() > size[1] * 0.4f;
        return (fromTop && tallEnough) || fromSide;
    }

    private boolean isSystemUiPackage(String packageName) {
        String normalized = packageName.toLowerCase(Locale.ROOT);
        return "com.android.systemui".equals(normalized)
                || normalized.contains("systemui")
                || normalized.contains("miui.notification");
    }

    private boolean isInputMethodPackage(String packageName) {
        String normalized = packageName.toLowerCase(Locale.ROOT);
        return normalized.contains("inputmethod")
                || normalized.contains(".ime")
                || normalized.endsWith(".ime")
                || normalized.contains("latin")
                || normalized.contains("keyboard");
    }

    private void bringKioskToFrontThrottled() {
        long now = System.currentTimeMillis();
        if (now - lastBringToFrontAt < 450L) {
            return;
        }
        lastBringToFrontAt = now;
        KioskPolicyManager.bringKioskToFront(this);
    }

    private void dismissNotificationShade() {
        long now = System.currentTimeMillis();
        if (now - lastShadeDismissAt < 200L) {
            return;
        }
        lastShadeDismissAt = now;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE);
            return;
        }
        performGlobalAction(GLOBAL_ACTION_BACK);
    }

    private void goHome() {
        long now = System.currentTimeMillis();
        if (now - lastHomeActionAt < 350L) {
            return;
        }
        lastHomeActionAt = now;
        KioskPolicyManager.bringKioskToFront(this);
        handler.postDelayed(() -> KioskPolicyManager.bringKioskToFront(this), 120L);
    }
}
