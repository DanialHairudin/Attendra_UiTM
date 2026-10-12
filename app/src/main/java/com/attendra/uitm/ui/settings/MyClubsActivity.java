package com.attendra.uitm.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import com.attendra.uitm.R;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.SeedCache;
import com.attendra.uitm.data.Subscriptions;
import com.attendra.uitm.data.UserRepository;
import com.attendra.uitm.databinding.ActivityMyClubsBinding;
import com.attendra.uitm.databinding.ItemClubRowBinding;
import com.attendra.uitm.model.Club;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.ui.BaseActivity;
import com.attendra.uitm.ui.common.Ui;
import com.attendra.uitm.util.HomeRouter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * My Clubs Page: the clubs a student joins (studentClubs/{uid}) or a lecturer
 * advises (clubAdvisors). Every club is listed with a switch: on = joined /
 * advising. Turning one off asks first. Each change is one multi-path write
 * (UserRepository.setClub), so studentClubs and clubMembers stay in sync.
 */
public class MyClubsActivity extends BaseActivity {

    private static final String EXTRA_ROLE = "role";

    public static void open(Context from, String role) {
        Intent intent = new Intent(from, MyClubsActivity.class);
        intent.putExtra(EXTRA_ROLE, role);
        from.startActivity(intent);
    }

    private ActivityMyClubsBinding binding;
    private final Subscriptions subs = new Subscriptions();
    private String role;
    private String uid;
    private List<String> clubIds;
    /** Clubs with a write in progress; their switch is disabled until it finishes. */
    private final Set<String> saving = new HashSet<>();
    private boolean seedReady;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMyClubsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        padForSystemBars(binding.clubsRoot);

        role = getIntent().getStringExtra(EXTRA_ROLE);
        boolean lecturer = Registration.ROLE_LECTURER.equals(role);
        binding.appbar.txtAppbarTitle.setText(lecturer
                ? R.string.label_clubs_lecturer : R.string.label_clubs_student);
        binding.appbar.btnBack.setOnClickListener(v -> finish());
        binding.txtClubsBody.setText(lecturer
                ? R.string.my_clubs_body_lecturer : R.string.my_clubs_body_student);
        Ui.setEmpty(binding.emptyClubs, R.drawable.ic_star,
                R.string.empty_my_clubs_title, R.string.empty_my_clubs_body);
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            HomeRouter.openSplash(this);
            return;
        }
        uid = user.getUid();

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
        binding.listClubs.removeAllViews();
        List<Club> all = seedReady ? SeedCache.allClubs() : null;
        if (clubIds == null || all == null || all.isEmpty()) {
            Ui.showListState(clubIds == null || all == null
                            ? Ui.ListState.LOADING : Ui.ListState.EMPTY,
                    binding.txtClubsLoading, binding.emptyClubs, binding.listClubs);
            return;
        }
        Ui.showListState(Ui.ListState.CONTENT, binding.txtClubsLoading, binding.emptyClubs,
                binding.listClubs);
        for (Club club : all) {
            ItemClubRowBinding row = ItemClubRowBinding.inflate(getLayoutInflater(),
                    binding.listClubs, false);
            row.clubShort.setText(club.shortName);
            row.clubName.setText(club.name);
            row.clubSwitch.setVisibility(View.VISIBLE);
            row.clubSwitch.setContentDescription(club.shortName);
            row.clubSwitch.setChecked(clubIds.contains(club.id));
            row.clubSwitch.setEnabled(!saving.contains(club.id));
            // Set after setChecked, so drawing the row does not count as a tap.
            row.clubSwitch.setOnCheckedChangeListener((b, on) -> {
                if (on) {
                    save(club, true);
                } else {
                    confirmLeave(club);
                }
            });
            row.getRoot().setOnClickListener(v -> {
                if (row.clubSwitch.isEnabled()) row.clubSwitch.toggle();
            });
            Ui.addRow(binding.listClubs, row.getRoot());
        }
    }

    /** Leaving (or no longer advising) is asked first; Cancel puts the switch back. */
    private void confirmLeave(Club club) {
        boolean lecturer = Registration.ROLE_LECTURER.equals(role);
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(lecturer ? R.string.club_leave_title_lecturer
                        : R.string.club_leave_title_student, club.shortName))
                .setMessage(lecturer ? R.string.club_leave_body_lecturer
                        : R.string.club_leave_body_student)
                .setPositiveButton(lecturer ? R.string.club_stop_advising : R.string.club_leave,
                        (d, w) -> save(club, false))
                .setNegativeButton(R.string.cancel, null)
                .setOnDismissListener(d -> {
                    if (!saving.contains(club.id)) render();
                })
                .show();
    }

    private void save(Club club, boolean on) {
        saving.add(club.id);
        render();
        new UserRepository().setClub(uid, role, club.id, on, new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                saving.remove(club.id);
                boolean lecturer = Registration.ROLE_LECTURER.equals(role);
                int msg = lecturer
                        ? (on ? R.string.club_advising : R.string.club_not_advising)
                        : (on ? R.string.club_joined : R.string.club_left);
                Snackbar.make(binding.getRoot(), getString(msg, club.shortName),
                        Snackbar.LENGTH_SHORT).show();
                render();
            }

            @Override
            public void onError(String message) {
                saving.remove(club.id);
                showError(message);
                render(); // the switch goes back to what the database says
            }
        });
    }

    private void showError(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }
}
