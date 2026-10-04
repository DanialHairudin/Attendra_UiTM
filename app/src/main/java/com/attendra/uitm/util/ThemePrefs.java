package com.attendra.uitm.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Saves and applies the Light / Dark / System theme choice.
 *
 * The choice is kept in SharedPreferences under "theme_mode" so it survives
 * app restarts. AttendraApp calls apply() once at start-up; the Settings
 * screen (step C4) will call save() and then apply().
 */
public final class ThemePrefs {

    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";
    public static final String MODE_SYSTEM = "system";

    private static final String PREFS_NAME = "attendra_prefs";
    private static final String KEY_THEME_MODE = "theme_mode";

    private ThemePrefs() {
    }

    /** Returns the saved mode, or "system" if the user never picked one. */
    public static String get(Context context) {
        return prefs(context).getString(KEY_THEME_MODE, MODE_SYSTEM);
    }

    public static void save(Context context, String mode) {
        prefs(context).edit().putString(KEY_THEME_MODE, mode).apply();
    }

    /** Tells AppCompat which night mode to use. Every Activity follows it. */
    public static void apply(String mode) {
        AppCompatDelegate.setDefaultNightMode(toNightMode(mode));
    }

    private static int toNightMode(String mode) {
        if (MODE_LIGHT.equals(mode)) {
            return AppCompatDelegate.MODE_NIGHT_NO;
        } else if (MODE_DARK.equals(mode)) {
            return AppCompatDelegate.MODE_NIGHT_YES;
        }
        // "system": follow the phone's dark theme setting
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
