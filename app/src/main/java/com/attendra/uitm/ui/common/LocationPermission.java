package com.attendra.uitm.ui.common;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.attendra.uitm.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Foreground location permission helpers, shared by the lecturer's "Use my location" button and
 * (later) the student's Check in screen, so both explain the permission the same way before
 * Android's own prompt appears.
 */
public final class LocationPermission {

    /** Pass this to a RequestMultiplePermissions launcher. */
    public static final String[] PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    private LocationPermission() {
    }

    public static boolean granted(Context c) {
        return ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Explains why location is needed. */
    public static void explain(Context c, @StringRes int body, Runnable onContinue) {
        new MaterialAlertDialogBuilder(c)
                .setTitle(R.string.location_permission_title)
                .setMessage(body)
                .setPositiveButton(R.string.location_permission_continue, (d, w) -> onContinue.run())
                .setNegativeButton(R.string.location_permission_not_now, null)
                .show();
    }
}
