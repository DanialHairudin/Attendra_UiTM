package com.attendra.uitm.ui.student;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.attendra.uitm.R;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.Db;
import com.attendra.uitm.data.Subscriptions;
import com.attendra.uitm.databinding.ActivityStudentMainBinding;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.ui.common.MainTabsActivity;
import com.attendra.uitm.ui.settings.SettingsFragment;
import com.attendra.uitm.util.NotificationPrefs;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;

/**
 * Student area. Bottom nav: Home · Attendance · Notifications · Settings
 * (CLAUDE.md 12.5). The Notifications item shows the number of unread
 * notifications as a gold badge (C8), unless the student turned
 * "Session notifications" off in Settings.
 */
public class StudentMainActivity extends MainTabsActivity {

    private BottomNavigationView nav;
    private final Subscriptions subs = new Subscriptions();
    /** Last unread count from the database, kept so the switch can redraw the badge. */
    private int unread;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!requireSignedIn()) return;
        ActivityStudentMainBinding binding = ActivityStudentMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setUpTabs(binding.mainContainer, binding.bottomNav, savedInstanceState);
        nav = binding.bottomNav;
    }

    @Override
    protected void onStart() {
        super.onStart();
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || nav == null) return;
        subs.listen(Db.root().child("notifications").child(uid), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                int count = 0;
                for (DataSnapshot n : snapshot.getChildren()) {
                    if (!Boolean.TRUE.equals(n.child("read").getValue(Boolean.class))) count++;
                }
                unread = count;
                refreshBadge();
            }

            @Override
            public void onError(String message) {
                unread = 0; // the tab itself shows the error
                refreshBadge();
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        subs.clear();
    }

    /** Shows or hides the badge; also called by Settings when the switch changes. */
    public void refreshBadge() {
        if (nav == null) return;
        if (unread == 0 || !NotificationPrefs.isOn(this)) {
            nav.removeBadge(R.id.nav_notifications);
            return;
        }
        BadgeDrawable badge = nav.getOrCreateBadge(R.id.nav_notifications);
        badge.setBackgroundColor(ContextCompat.getColor(this, R.color.gold));
        badge.setBadgeTextColor(ContextCompat.getColor(this, R.color.onGold));
        badge.setNumber(unread);
        badge.setContentDescriptionQuantityStringsResource(R.plurals.notifications_badge);
    }

    @Override
    public String role() {
        return Registration.ROLE_STUDENT;
    }

    @Override
    protected Fragment createTab(int itemId) {
        if (itemId == R.id.nav_attendance) return new MyAttendanceFragment();
        if (itemId == R.id.nav_notifications) return new NotificationsFragment();
        if (itemId == R.id.nav_settings) return SettingsFragment.newInstance(role());
        return new StudentHomeFragment();
    }
}
