package com.attendra.uitm.ui.lecturer;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.attendra.uitm.R;
import com.attendra.uitm.databinding.ActivityLecturerMainBinding;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.ui.common.ComingSoonActivity;
import com.attendra.uitm.ui.common.MainTabsActivity;
import com.attendra.uitm.ui.common.PlaceholderTabFragment;
import com.attendra.uitm.ui.settings.SettingsFragment;

/**
 * Lecturer area. Bottom nav: Home · Classes · (+) · History · Settings
 * (CLAUDE.md 12.5). The gold (+) opens the Create session wizard (C6).
 */
public class LecturerMainActivity extends MainTabsActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!requireSignedIn()) return;
        ActivityLecturerMainBinding binding = ActivityLecturerMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setUpTabs(binding.mainContainer, binding.bottomNav, savedInstanceState);
        binding.btnFab.setOnClickListener(v -> openCreateSession());
    }

    @Override
    public String role() {
        return Registration.ROLE_LECTURER;
    }

    @Override
    protected Fragment createTab(int itemId) {
        if (itemId == R.id.nav_classes) return new ClassesFragment();
        if (itemId == R.id.nav_history) {
            return PlaceholderTabFragment.newInstance(R.string.history_title, R.drawable.ic_clock,
                    R.string.history_later);
        }
        if (itemId == R.id.nav_settings) return SettingsFragment.newInstance(role());
        return new LecturerHomeFragment();
    }

    /** Placeholder until the Create session wizard is built (C6). */
    public void openCreateSession() {
        ComingSoonActivity.open(this, R.string.create_session, R.string.create_session_later);
    }
}
