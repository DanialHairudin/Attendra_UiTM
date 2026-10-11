package com.attendra.uitm.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

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
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;

import java.util.List;

/**
 * My Clubs Page: the clubs a student joins (studentClubs/{uid}) or a lecturer
 * advises (clubAdvisors). Read-only for now; joining and leaving come in C4.
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
    private List<String> clubIds;
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
        if (clubIds == null || !seedReady || clubIds.isEmpty()) {
            Ui.showListState(clubIds == null || !seedReady
                            ? Ui.ListState.LOADING : Ui.ListState.EMPTY,
                    binding.txtClubsLoading, binding.emptyClubs, binding.listClubs);
            return;
        }
        Ui.showListState(Ui.ListState.CONTENT, binding.txtClubsLoading, binding.emptyClubs,
                binding.listClubs);
        for (String id : clubIds) {
            Club club = SeedCache.club(id);
            ItemClubRowBinding row = ItemClubRowBinding.inflate(getLayoutInflater(),
                    binding.listClubs, false);
            row.clubShort.setText(club != null ? club.shortName : id);
            row.clubName.setText(club != null ? club.name : "");
            Ui.addRow(binding.listClubs, row.getRoot());
        }
    }

    private void showError(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }
}
