package com.attendra.uitm.util;

import android.app.Activity;
import android.content.Intent;

import com.attendra.uitm.model.Registration;
import com.attendra.uitm.ui.lecturer.LecturerMainActivity;
import com.attendra.uitm.ui.splash.SplashActivity;
import com.attendra.uitm.ui.student.StudentMainActivity;

/** Sends the user to the right area of the app and clears the back stack. */
public final class HomeRouter {

    private HomeRouter() {
    }

    /** Opens LecturerMain or StudentMain depending on users/{uid}/role. */
    public static void openHome(Activity from, String role) {
        Class<?> target = Registration.ROLE_LECTURER.equals(role)
                ? LecturerMainActivity.class
                : StudentMainActivity.class;
        start(from, target);
    }

    /** Back to the splash screen, e.g. after signing out. */
    public static void openSplash(Activity from) {
        start(from, SplashActivity.class);
    }

    // NEW_TASK + CLEAR_TASK removes every screen behind the new one, so pressing
    // Back on the home screen can't return to Login (or Login to a home screen).
    private static void start(Activity from, Class<?> target) {
        Intent intent = new Intent(from, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        from.startActivity(intent);
        from.finish();
    }
}
