package com.attendra.uitm.util;

import com.attendra.uitm.BuildConfig;
import com.attendra.uitm.model.Registration;

/**
 * The two tester accounts (CLAUDE.md 12.4). Their emails and passwords come from
 * local.properties through BuildConfig, and only the DEBUG build type gets them.
 * In release every value is "", so available() is false and the tester panel
 * on the Login Page never appears.
 */
public final class TestAccounts {

    private TestAccounts() {
    }

    /** True only in a debug build. Callers check this before showing anything. */
    public static boolean enabled() {
        return BuildConfig.DEBUG;
    }

    /** True if the test login for this role is filled in. */
    public static boolean available(String role) {
        return enabled() && !email(role).isEmpty() && !password(role).isEmpty();
    }

    public static String email(String role) {
        return Registration.ROLE_LECTURER.equals(role)
                ? BuildConfig.TEST_LECTURER_EMAIL : BuildConfig.TEST_STUDENT_EMAIL;
    }

    public static String password(String role) {
        return Registration.ROLE_LECTURER.equals(role)
                ? BuildConfig.TEST_LECTURER_PASS : BuildConfig.TEST_STUDENT_PASS;
    }
}
