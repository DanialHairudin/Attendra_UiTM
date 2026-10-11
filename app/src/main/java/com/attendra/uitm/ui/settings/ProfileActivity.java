package com.attendra.uitm.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.attendra.uitm.R;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.SeedCache;
import com.attendra.uitm.data.Subscriptions;
import com.attendra.uitm.data.UserRepository;
import com.attendra.uitm.databinding.ActivityProfileBinding;
import com.attendra.uitm.databinding.ItemInfoRowBinding;
import com.attendra.uitm.model.Club;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.ui.BaseActivity;
import com.attendra.uitm.ui.common.Ui;
import com.attendra.uitm.util.HomeRouter;
import com.attendra.uitm.util.Names;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;

import java.util.List;

/**
 * Profile Page (read-only for now; editing name and phone comes in C4).
 * Shows lecturers/{uid} or students/{uid} and the user's clubs, live.
 */
public class ProfileActivity extends BaseActivity {

    private static final String EXTRA_ROLE = "role";

    public static void open(Context from, String role) {
        Intent intent = new Intent(from, ProfileActivity.class);
        intent.putExtra(EXTRA_ROLE, role);
        from.startActivity(intent);
    }

    private ActivityProfileBinding binding;
    private final Subscriptions subs = new Subscriptions();
    private String role;
    private DataSnapshot profile;
    private List<String> clubIds;
    private boolean seedReady;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        padForSystemBars(binding.profileRoot);

        role = getIntent().getStringExtra(EXTRA_ROLE);
        boolean lecturer = Registration.ROLE_LECTURER.equals(role);
        binding.appbar.txtAppbarTitle.setText(R.string.profile_title);
        binding.appbar.btnBack.setOnClickListener(v -> finish());
        binding.txtRole.setText(lecturer ? R.string.home_role_lecturer : R.string.home_role_student);
        binding.txtClubsTitle.setText(lecturer
                ? R.string.label_clubs_lecturer : R.string.label_clubs_student);
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            HomeRouter.openSplash(this);
            return;
        }
        String uid = user.getUid();

        SeedCache.load(new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                seedReady = true;
                render();
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });

        subs.listen(UserRepository.profileRef(uid, role), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                profile = snapshot;
                render();
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });

        subs.listen(UserRepository.clubsQuery(uid, role), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                clubIds = UserRepository.clubIds(snapshot, uid, role);
                render();
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        subs.clear();
    }

    private void render() {
        if (profile != null) {
            String name = text("name");
            binding.txtName.setText(name);
            binding.txtAvatar.setText(Names.initials(name));

            binding.listInfo.removeAllViews();
            addInfo(R.string.profile_email, text("email"));
            addInfo(R.string.profile_phone, text("phone"));
            if (Registration.ROLE_LECTURER.equals(role)) {
                addInfo(R.string.profile_staff_id, text("staffId"));
            } else {
                addInfo(R.string.profile_student_id, text("studentId"));
                addInfo(R.string.profile_programme, text("programme"));
                Long part = profile.child("part").getValue(Long.class);
                addInfo(R.string.profile_part, part == null ? "" : String.valueOf(part));
                addInfo(R.string.profile_group, text("groupId"));
            }
        }

        if (clubIds != null && seedReady) {
            binding.rowClubs.removeAllViews();
            if (clubIds.isEmpty()) {
                TextView none = new TextView(this);
                none.setText(R.string.no_clubs_yet);
                none.setTextAppearance(R.style.TextAppearance_Attendra_Caption);
                binding.rowClubs.addView(none);
            } else {
                for (String id : clubIds) {
                    Club club = SeedCache.club(id);
                    Ui.goldBadge(binding.rowClubs, club != null ? club.shortName : id);
                }
            }
        }
    }

    private void addInfo(@StringRes int label, String value) {
        ItemInfoRowBinding row = ItemInfoRowBinding.inflate(getLayoutInflater(),
                binding.listInfo, false);
        row.infoLabel.setText(label);
        row.infoValue.setText(value);
        Ui.addRow(binding.listInfo, row.getRoot());
    }

    private String text(String key) {
        String value = profile.child(key).getValue(String.class);
        return value == null ? "" : value;
    }

    private void showError(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }
}
