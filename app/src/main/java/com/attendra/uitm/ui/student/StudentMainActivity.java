package com.attendra.uitm.ui.student;

import com.attendra.uitm.R;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.ui.PlaceholderHomeActivity;

/**
 * Student area. For now (C3) it is a placeholder; the bottom nav with
 * Home · Attendance · Notifications · Profile is built from C8 onwards.
 */
public class StudentMainActivity extends PlaceholderHomeActivity {

    @Override
    protected String role() {
        return Registration.ROLE_STUDENT;
    }

    @Override
    protected int roleLabel() {
        return R.string.home_role_student;
    }

    @Override
    protected int placeholderText() {
        return R.string.home_student_placeholder;
    }
}
