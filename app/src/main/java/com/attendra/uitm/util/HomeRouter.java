package com.attendra.uitm.util;

import android.app.Activity;
import android.content.Intent;

import com.attendra.uitm.data.AuthJava;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.ui.lecturer.LecturerMainPage;
import com.attendra.uitm.ui.settings.PreferredNamePage;
import com.attendra.uitm.ui.splash.SplashPage;
import com.attendra.uitm.ui.student.PhoneCheckPage;
import com.attendra.uitm.ui.student.StudentMainPage;

/** Sends the user to the right area of the app and clears the back stack. */
public final class HomeRouter {

    private HomeRouter() {
    }

    /**
     * Follows a verified user's route: the Preferred Name Page first if they have not picked a name
     * yet, otherwise their dashboard.
     */
    public static void open(Activity from, AuthJava.Route route) {
        if (route.needsName) {
            Intent intent = PreferredNamePage.firstTimeIntent(from, route.role);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            from.startActivity(intent);
            from.finish();
        } else {
            openHome(from, route.role);
        }
    }

    /** Lecturers go to their dashboard; students go through the phone check first. */
    public static void openHome(Activity from, String role) {
        if (Registration.ROLE_LECTURER.equals(role)) {
            openDashboard(from, role);
        } else {
            start(from, PhoneCheckPage.class);
        }
    }

    /** Opens LecturerMain or StudentMain depending on roles/{uid}. */
    public static void openDashboard(Activity from, String role) {
        Class<?> target;
        if (Registration.ROLE_LECTURER.equals(role)) {
            target = LecturerMainPage.class;
        } else {
            target = StudentMainPage.class;
        }
        start(from, target);
    }

    /** Back to the splash screen, e.g. after signing out. */
    public static void openSplash(Activity from) {
        start(from, SplashPage.class);
    }

    // NEW_TASK + CLEAR_TASK removes every screen behind the new one, so pressing Back on the home
    // screen can't return to Login (or Login to a home screen).
    private static void start(Activity from, Class<?> target) {
        Intent intent = new Intent(from, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        from.startActivity(intent);
        from.finish();
    }
}
