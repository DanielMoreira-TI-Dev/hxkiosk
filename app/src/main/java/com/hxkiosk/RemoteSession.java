package com.hxkiosk;

public final class RemoteSession {

    private static volatile long lastSeenAt;

    private RemoteSession() {
    }

    public static void ping() {
        lastSeenAt = System.currentTimeMillis();
    }

    public static boolean isActive() {
        return lastSeenAt > 0L && System.currentTimeMillis() - lastSeenAt < 10000L;
    }
}
