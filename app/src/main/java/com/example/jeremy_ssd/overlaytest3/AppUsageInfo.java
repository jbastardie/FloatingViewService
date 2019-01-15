package com.example.jeremy_ssd.overlaytest3;

import android.app.usage.UsageStatsManager;
import android.graphics.drawable.Drawable;

public class AppUsageInfo {
    private UsageStatsManager mUsageStatsManager;
    Drawable appIcon;
    String appName, packageName;
    long timeInForeground;
    int launchCount;
    Float co2;

    AppUsageInfo(String pName) {
        this.packageName=pName;
    }

}
