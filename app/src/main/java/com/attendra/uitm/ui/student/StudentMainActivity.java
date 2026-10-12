package com.attendra.uitm.ui.student;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.attendra.uitm.R;
import com.attendra.uitm.databinding.ActivityStudentMainBinding;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.ui.common.MainTabsActivity;
import com.attendra.uitm.ui.common.PlaceholderTabFragment;
import com.attendra.uitm.ui.settings.SettingsFragment;

/**
 * Student area. Bottom nav: Home · Attendance · Notifications · Settings
 * (CLAUDE.md 12.5).
 */
public class StudentMainActivity extends MainTabsActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!requireSignedIn()) return;
        ActivityStudentMainBinding binding = ActivityStudentMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setUpTabs(binding.mainContainer, binding.bottomNav, savedInstanceState);
    }

    @Override
    public String role() {
        return Registration.ROLE_STUDENT;
    }

    @Override
    protected Fragment createTab(int itemId) {
        if (itemId == R.id.nav_attendance) return new MyAttendanceFragment();
        if (itemId == R.id.nav_notifications) {
            return PlaceholderTabFragment.newInstance(R.string.notifications_title,
                    R.drawable.ic_bell, R.string.notifications_later);
        }
        if (itemId == R.id.nav_settings) return SettingsFragment.newInstance(role());
        return new StudentHomeFragment();
    }
}
