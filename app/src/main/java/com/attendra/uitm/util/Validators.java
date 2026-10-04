package com.attendra.uitm.util;

import static com.attendra.uitm.AttendraApp.str;

import android.util.Patterns;

import androidx.annotation.Nullable;

import com.attendra.uitm.BuildConfig;
import com.attendra.uitm.R;
import com.attendra.uitm.model.Registration;

/**
 * Form checks for the Auth screens. Each method returns null when the value is
 * fine, or a short message to show under the field. The wording lives in
 * res/values/strings.xml (the error_* strings).
 */
public final class Validators {

    public static final int MIN_PASSWORD = 8;
    public static final String STUDENT_DOMAIN = "@student.uitm.edu.my";
    public static final String STAFF_DOMAIN = "@uitm.edu.my";

    private Validators() {
    }

    @Nullable
    public static String name(String s) {
        if (s.isEmpty()) return str(R.string.error_name_required);
        if (s.length() < 3) return str(R.string.error_name_short);
        return null;
    }

    /** Basic email check, used on Login and Forgot password. */
    @Nullable
    public static String email(String s) {
        if (s.isEmpty()) return str(R.string.error_email_required);
        if (!Patterns.EMAIL_ADDRESS.matcher(s).matches()) return str(R.string.error_email_invalid);
        return null;
    }

    /**
     * Email check on Register. When BuildConfig.REQUIRE_UITM_EMAIL is true (release),
     * students must use @student.uitm.edu.my and lecturers @uitm.edu.my.
     * It is false in debug so Gmail can be used for testing.
     */
    @Nullable
    public static String registerEmail(String s, String role) {
        String basic = email(s);
        if (basic != null) return basic;
        if (BuildConfig.REQUIRE_UITM_EMAIL) {
            String lower = s.toLowerCase();
            if (Registration.ROLE_STUDENT.equals(role) && !lower.endsWith(STUDENT_DOMAIN)) {
                return str(R.string.error_email_student_domain, STUDENT_DOMAIN);
            }
            if (Registration.ROLE_LECTURER.equals(role) && !lower.endsWith(STAFF_DOMAIN)) {
                return str(R.string.error_email_staff_domain, STAFF_DOMAIN);
            }
        }
        return null;
    }

    /**
     * Turns what the user typed after "+60" into digits only. A leading 0 is
     * dropped, so "012-345 6789" and "12-345 6789" both become "123456789".
     */
    public static String phoneDigits(String s) {
        String digits = s.replaceAll("[^0-9]", "");
        if (digits.startsWith("0")) digits = digits.substring(1);
        return digits;
    }

    /** Malaysian mobile numbers are 9 or 10 digits after +60. */
    @Nullable
    public static String phone(String s) {
        String digits = phoneDigits(s);
        if (digits.isEmpty()) return str(R.string.error_phone_required);
        if (digits.length() < 9 || digits.length() > 10) return str(R.string.error_phone_invalid);
        return null;
    }

    /** Login: the password only has to be filled in. */
    @Nullable
    public static String loginPassword(String s) {
        if (s.isEmpty()) return str(R.string.error_password_required);
        return null;
    }

    /** Register: a new password needs at least MIN_PASSWORD characters. */
    @Nullable
    public static String password(String s) {
        if (s.isEmpty()) return str(R.string.error_password_required);
        if (s.length() < MIN_PASSWORD) return str(R.string.error_password_short);
        return null;
    }

    @Nullable
    public static String confirmPassword(String password, String confirm) {
        if (confirm.isEmpty()) return str(R.string.error_confirm_required);
        if (!confirm.equals(password)) return str(R.string.error_password_mismatch);
        return null;
    }

    /** UiTM student IDs are 10 digits, e.g. 2025125181. */
    @Nullable
    public static String studentId(String s) {
        if (s.isEmpty()) return str(R.string.error_student_id_required);
        if (!s.matches("\\d{10}")) return str(R.string.error_student_id_invalid);
        return null;
    }

    @Nullable
    public static String staffId(String s) {
        if (s.isEmpty()) return str(R.string.error_staff_id_required);
        if (!s.matches("[A-Za-z0-9]{3,20}")) return str(R.string.error_staff_id_invalid);
        return null;
    }
}
