package com.sallo.kyosk.model;

import android.graphics.drawable.Drawable;

public class AppModel {

    private final String appName;
    private final String packageName;
    private final Drawable icon;
    private boolean allowed;

    public AppModel(String appName, String packageName, Drawable icon) {
        this.appName = appName;
        this.packageName = packageName;
        this.icon = icon;
    }

    public String getAppName() {
        return appName;
    }

    public String getPackageName() {
        return packageName;
    }

    public Drawable getIcon() {
        return icon;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }
}
