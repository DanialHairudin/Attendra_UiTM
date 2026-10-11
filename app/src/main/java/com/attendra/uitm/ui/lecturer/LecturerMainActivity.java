package com.attendra.uitm.ui.lecturer;

import com.attendra.uitm.R;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.ui.PlaceholderHomeActivity;

/**
 * Lecturer area. For now (C3) it is a placeholder; the bottom nav with
 * Home · Classes · History · Profile is built from C5 onwards.
 */
public class LecturerMainActivity extends PlaceholderHomeActivity {

    @Override
    protected String role() {
        return Registration.ROLE_LECTURER;
    }

    @Override
    protected int roleLabel() {
        return R.string.home_role_lecturer;
    }

    @Override
    protected int placeholderText() {
        return R.string.home_lecturer_placeholder;
    }
}
