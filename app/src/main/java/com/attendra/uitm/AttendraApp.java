package com.attendra.uitm;

import android.app.Application;

import androidx.annotation.StringRes;

import com.attendra.uitm.map.OsmSetup;
import com.attendra.uitm.util.ThemePrefs;

/**
 * Custom Application class. Android creates it before any Activity, so this is
 * the right place to apply the saved theme. Applying it here means the first
 * screen already opens in the right colours, with no light-to-dark flash.
 */
public class AttendraApp extends Application {

    private static AttendraApp instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        // OpenStreetMap setup (User-Agent, tile cache folder) before any map is shown.
        OsmSetup.init(this);
        ThemePrefs.apply(ThemePrefs.get(this));
    }

    /**
     * Reads a message from res/values/strings.xml. Used by classes that have no
     * Activity at hand (repositories, validators), so every user-facing message
     * still lives in strings.xml.
     */
    public static String str(@StringRes int id, Object... args) {
        return instance.getString(id, args);
    }
}
