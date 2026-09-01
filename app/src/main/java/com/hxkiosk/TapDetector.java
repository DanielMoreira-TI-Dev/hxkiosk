package com.hxkiosk;

import android.os.SystemClock;

public class TapDetector {

    private final int requiredTaps;
    private final long maxIntervalMs;
    private int currentTaps;
    private long lastTapTimestamp;

    public TapDetector(int requiredTaps, long maxIntervalMs) {
        this.requiredTaps = requiredTaps;
        this.maxIntervalMs = maxIntervalMs;
    }

    public boolean registerTap() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastTapTimestamp > maxIntervalMs) {
            currentTaps = 0;
        }
        lastTapTimestamp = now;
        currentTaps++;
        if (currentTaps >= requiredTaps) {
            reset();
            return true;
        }
        return false;
    }

    public void reset() {
        currentTaps = 0;
        lastTapTimestamp = 0L;
    }
}
