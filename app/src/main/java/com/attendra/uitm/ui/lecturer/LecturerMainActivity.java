package com.attendra.uitm.ui.lecturer;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.attendra.uitm.R;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.Db;
import com.attendra.uitm.data.TeachingRepository;
import com.attendra.uitm.data.UserRepository;
import com.attendra.uitm.databinding.ActivityLecturerMainBinding;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.ui.session.CreateSessionActivity;
import com.attendra.uitm.ui.common.MainTabsActivity;
import com.attendra.uitm.ui.settings.SettingsFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;

/**
 * Lecturer area. Bottom nav: Home · Classes · (+) · History · Settings
 * (CLAUDE.md 12.5). The gold (+) opens the Create session wizard (C6).
 */
public class LecturerMainActivity extends MainTabsActivity {

    private static final String EXTRA_CLASSES_TAB = "classes_tab";

    /** Brings the lecturer area back to the front on the Classes tab (from Profile). */
    public static void openClassesTab(Context from) {
        from.startActivity(new Intent(from, LecturerMainActivity.class)
                .putExtra(EXTRA_CLASSES_TAB, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    /** True while the Create session guard is reading, so a double tap opens one wizard. */
    private boolean checkingCreate;

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
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra(EXTRA_CLASSES_TAB, false)) selectTab(R.id.nav_classes);
    }

    @Override
    public String role() {
        return Registration.ROLE_LECTURER;
    }

    @Override
    protected Fragment createTab(int itemId) {
        if (itemId == R.id.nav_classes) return new ClassesFragment();
        if (itemId == R.id.nav_history) return new HistoryFragment();
        if (itemId == R.id.nav_settings) return SettingsFragment.newInstance(role());
        return new LecturerHomeFragment();
    }

    /**
     * Opens the Create Session wizard (C6). Guard (C5b): with no teaching class
     * this semester and no advised club there is nothing to pick, so the
     * lecturer is asked to add their classes first. If a read fails, the
     * wizard opens anyway and shows its own error.
     */
    public void openCreateSession() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || checkingCreate) return;
        checkingCreate = true;
        TeachingRepository.hasCurrent(uid, new Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean hasClasses) {
                if (hasClasses) {
                    startWizard();
                } else {
                    checkClubs(uid);
                }
            }

            @Override
            public void onError(String message) {
                startWizard();
            }
        });
    }

    private void checkClubs(String uid) {
        String role = Registration.ROLE_LECTURER;
        Db.readOnce(UserRepository.clubsQuery(uid, role), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                if (!UserRepository.clubIds(snapshot, uid, role).isEmpty()) {
                    startWizard();
                    return;
                }
                checkingCreate = false;
                if (isFinishing()) return;
                new MaterialAlertDialogBuilder(LecturerMainActivity.this)
                        .setTitle(R.string.teach_needed)
                        .setMessage(R.string.teach_needed_body)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.teach_add_button, (d, w) ->
                                AddTeachingClassActivity.open(LecturerMainActivity.this))
                        .show();
            }

            @Override
            public void onError(String message) {
                startWizard();
            }
        });
    }

    private void startWizard() {
        checkingCreate = false;
        if (!isFinishing()) CreateSessionActivity.open(this);
    }
}
