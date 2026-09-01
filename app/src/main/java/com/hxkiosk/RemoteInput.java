package com.hxkiosk;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.Nullable;

import org.json.JSONObject;

public final class RemoteInput {

    private RemoteInput() {
    }

    public static boolean inject(JSONObject body) {
        String key = body.optString("key", "");
        String code = body.optString("code", "");
        boolean shift = body.optBoolean("shift", false);
        boolean ctrl = body.optBoolean("ctrl", false);
        boolean alt = body.optBoolean("alt", false);
        if (key.isEmpty() && code.isEmpty()) {
            return false;
        }

        Activity activity = HxKioskApp.get() == null ? null : HxKioskApp.get().getResumedActivity();
        boolean ok;
        if (activity instanceof MainKioskActivity) {
            ok = ((MainKioskActivity) activity).injectRemoteKey(key, code, shift, ctrl, alt);
        } else if (activity != null) {
            activity.runOnUiThread(() -> {
                View target = activity.getCurrentFocus();
                RemoteInput.dispatchEvents(activity, target, key, code, shift, ctrl, alt);
            });
            ok = true;
        } else {
            ok = KioskAccessibilityService.typeKey(key, code);
        }
        return ok;
    }

    public static void showIme(@Nullable Activity activity) {
        if (activity == null) {
            return;
        }
        activity.runOnUiThread(() -> {
            View target = activity.getCurrentFocus();
            if (target == null) {
                target = activity.getWindow().getDecorView();
            }
            target.requestFocus();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = activity.getWindow().getInsetsController();
                if (controller != null) {
                    controller.show(WindowInsets.Type.ime()
                            | WindowInsets.Type.statusBars()
                            | WindowInsets.Type.navigationBars());
                }
            }
            InputMethodManager manager =
                    (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) {
                manager.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    public static boolean injectPointer(JSONObject body) {
        String type = body.optString("type", "tap");
        float x = (float) body.optDouble("x", -1);
        float y = (float) body.optDouble("y", -1);
        float x2 = (float) body.optDouble("x2", x);
        float y2 = (float) body.optDouble("y2", y);
        long duration = body.optLong("duration", 160);
        if (x < 0f || x > 1f || y < 0f || y > 1f) {
            return false;
        }

        boolean swipe = "swipe".equals(type);
        float travel = (float) Math.hypot(x2 - x, y2 - y);
        boolean tap = !swipe || travel < 0.03f;
        if (tap && y >= 0.92f) {
            String nav = x < 0.38f ? "back" : (x > 0.62f ? "recents" : "home");
            return KioskAccessibilityService.performNav(nav);
        }
        boolean notificationSwipe = swipe && !tap && y <= 0.06f && (y2 - y) >= 0.18f;

        Activity activity = HxKioskApp.get() == null ? null : HxKioskApp.get().getResumedActivity();
        float[] size = KioskAccessibilityService.displaySize(activity);
        float[] screen = {
                x * Math.max(1f, size[0] - 1f),
                y * Math.max(1f, size[1] - 1f),
                x2 * Math.max(1f, size[0] - 1f),
                y2 * Math.max(1f, size[1] - 1f)
        };
        final boolean[] nativeConsumed = { false };
        if (activity != null) {
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            activity.runOnUiThread(() -> {
                if (activity instanceof MainKioskActivity) {
                    nativeConsumed[0] = ((MainKioskActivity) activity).injectRemotePointer(
                            x, y, x2, y2, !tap, duration
                    );
                }
                latch.countDown();
            });
            try {
                latch.await(400, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        if (nativeConsumed[0]) {
            return true;
        }

        if (notificationSwipe) {
            return KioskAccessibilityService.openNotificationShade()
                    || KioskAccessibilityService.swipeNormalized(x, 0f, x2, Math.min(1f, y2), Math.max(duration, 280L));
        }
        if (tap) {
            return KioskAccessibilityService.tapPixels(screen[0], screen[1]);
        }
        return KioskAccessibilityService.swipePixels(screen[0], screen[1], screen[2], screen[3], duration);
    }

    static boolean dispatchEvents(Activity activity, View target, String key, String code,
                                  boolean shift, boolean ctrl, boolean alt) {
        int meta = 0;
        if (shift) {
            meta |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
        }
        if (ctrl) {
            meta |= KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        }
        if (alt) {
            meta |= KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
        }

        if (key.length() == 1 && !ctrl && !alt) {
            KeyCharacterMap map = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
            KeyEvent[] events = map.getEvents(key.toCharArray());
            if (events != null && events.length > 0) {
                boolean ok = false;
                for (KeyEvent event : events) {
                    ok = dispatch(activity, target, event) || ok;
                }
                return ok;
            }
        }

        int keyCode = mapKeyCode(key, code);
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            return false;
        }
        long now = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta);
        KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta);
        return dispatch(activity, target, down) | dispatch(activity, target, up);
    }

    private static boolean dispatch(Activity activity, View target, KeyEvent event) {
        if (target != null) {
            return target.dispatchKeyEvent(event);
        }
        return activity.dispatchKeyEvent(event);
    }

    static int mapKeyCode(String key, String code) {
        switch (code) {
            case "Enter":
            case "NumpadEnter":
                return KeyEvent.KEYCODE_ENTER;
            case "Backspace":
                return KeyEvent.KEYCODE_DEL;
            case "Delete":
                return KeyEvent.KEYCODE_FORWARD_DEL;
            case "Tab":
                return KeyEvent.KEYCODE_TAB;
            case "Escape":
                return KeyEvent.KEYCODE_ESCAPE;
            case "Space":
                return KeyEvent.KEYCODE_SPACE;
            case "ArrowLeft":
                return KeyEvent.KEYCODE_DPAD_LEFT;
            case "ArrowRight":
                return KeyEvent.KEYCODE_DPAD_RIGHT;
            case "ArrowUp":
                return KeyEvent.KEYCODE_DPAD_UP;
            case "ArrowDown":
                return KeyEvent.KEYCODE_DPAD_DOWN;
            case "Home":
                return KeyEvent.KEYCODE_MOVE_HOME;
            case "End":
                return KeyEvent.KEYCODE_MOVE_END;
            case "PageUp":
                return KeyEvent.KEYCODE_PAGE_UP;
            case "PageDown":
                return KeyEvent.KEYCODE_PAGE_DOWN;
            default:
                break;
        }
        if (key.length() == 1) {
            char character = Character.toUpperCase(key.charAt(0));
            if (character >= 'A' && character <= 'Z') {
                return KeyEvent.KEYCODE_A + (character - 'A');
            }
            if (character >= '0' && character <= '9') {
                return KeyEvent.KEYCODE_0 + (character - '0');
            }
        }
        return KeyEvent.KEYCODE_UNKNOWN;
    }
}
