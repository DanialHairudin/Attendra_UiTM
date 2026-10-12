package com.attendra.uitm.util;

import android.content.Context;

/**
 * The "Session notifications" switch in Settings (CLAUDE.md section 9),
 * kept in the same SharedPreferences file as the theme.
 *
 * Notifications are in-app only (no push on the Spark plan), so "off" means
 * the unread badge on the Notifications tab is hidden. The list itself is
 * still there when the student opens the tab.
 */
public final class NotificationPrefs {

    private static final String PREFS_NAME = "attendra_prefs";
    private static final String KEY_ON = "notifications_on";

    private NotificationPrefs() {
    }

    /** On unless the student turned it off. */
    public static boolean isOn(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ON, true);
    }

    public static void save(Context context, boolean on) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ON, on).apply();
    }
}
