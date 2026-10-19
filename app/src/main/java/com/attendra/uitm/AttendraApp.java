package com.attendra.uitm;

import android.app.Application;

import androidx.annotation.StringRes;

import com.attendra.uitm.data.Db;
import com.attendra.uitm.map.OsmSetup;
import com.attendra.uitm.util.ThemePrefs;
import com.google.firebase.auth.FirebaseAuth;

/** Custom Application class. */
public class AttendraApp extends Application {

    private static AttendraApp instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        if (BuildConfig.USE_EMULATORS) {
            // Debug only: local Auth + Database emulators, before anything signs in or reads.
            FirebaseAuth.getInstance().useEmulator(BuildConfig.EMULATOR_HOST, 9099);
            Db.get();
        }
        // OpenStreetMap setup (User-Agent, tile cache folder) before any map is shown.
        OsmSetup.init(this);
        ThemePrefs.apply(ThemePrefs.get(this));
    }

    /** Reads a message from res/values/strings.xml. */
    public static String str(@StringRes int id, Object... args) {
        return instance.getString(id, args);
    }
}
