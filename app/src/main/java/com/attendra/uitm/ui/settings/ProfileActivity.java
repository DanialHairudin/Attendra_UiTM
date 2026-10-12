package com.attendra.uitm.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.attendra.uitm.R;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.Db;
import com.attendra.uitm.data.SeedCache;
import com.attendra.uitm.data.Subscriptions;
import com.attendra.uitm.data.TeachingRepository;
import com.attendra.uitm.data.UserRepository;
import com.attendra.uitm.databinding.ActivityProfileBinding;
import com.attendra.uitm.databinding.ItemInfoRowBinding;
import com.attendra.uitm.databinding.SheetPhoneBinding;
import com.attendra.uitm.model.Club;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.ui.BaseActivity;
import com.attendra.uitm.ui.common.Ui;
import com.attendra.uitm.ui.lecturer.LecturerMainActivity;
import com.attendra.uitm.util.HomeRouter;
import com.attendra.uitm.util.Names;
import com.attendra.uitm.util.Validators;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;

import java.util.List;

/**
 * Profile Page. Shows lecturers/{uid} or students/{uid} and the user's clubs, live.
 * Preferred name (and Title for lecturers) and the phone number can be changed
 * here. The full name, email and IDs are fixed; the greeting uses the preferred
 * name (CLAUDE.md 12.6). The clubs card opens My Clubs.
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
    /** Lecturer only: classes this semester (C5b). Null = not loaded yet. */
    private Integer classCount;

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
        binding.cardClubs.setOnClickListener(v -> MyClubsActivity.open(this, role));
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

        if (Registration.ROLE_LECTURER.equals(role)) {
            subs.listen(TeachingRepository.ref(uid), new Callback<DataSnapshot>() {
                @Override
                public void onSuccess(DataSnapshot snapshot) {
                    classCount = TeachingRepository.current(TeachingRepository.parse(snapshot)).size();
                    render();
                }

                @Override
                public void onError(String message) {
                    showError(message);
                }
            });
        }

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
            // Editable rows (CLAUDE.md 12.6): both open the Preferred Name Page.
            String preferred = text("preferredName");
            addEditable(R.string.profile_preferred_name, preferred.isEmpty()
                    ? getString(R.string.profile_not_set) : preferred);
            boolean lecturer = Registration.ROLE_LECTURER.equals(role);
            if (lecturer) {
                String title = text("title");
                addEditable(R.string.profile_title_row, title.isEmpty()
                        ? getString(R.string.name_title_none) : title);
                // C5b: opens the Classes tab, where classes are added or changed.
                addTappable(R.string.profile_teaching_classes, classCount == null
                        ? getString(R.string.dash)
                        : getResources().getQuantityString(R.plurals.classes_this_semester,
                                classCount, classCount), v -> {
                    LecturerMainActivity.openClassesTab(this);
                    finish();
                });
            }
            addInfo(R.string.profile_email, text("email"));
            addTappable(R.string.profile_phone, text("phone"), v -> editPhone());
            if (lecturer) {
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

    private ItemInfoRowBinding addInfo(@StringRes int label, String value) {
        ItemInfoRowBinding row = ItemInfoRowBinding.inflate(getLayoutInflater(),
                binding.listInfo, false);
        row.infoLabel.setText(label);
        row.infoValue.setText(value);
        Ui.addRow(binding.listInfo, row.getRoot());
        return row;
    }

    /** A row with a chevron that opens the Preferred Name Page. */
    private void addEditable(@StringRes int label, String value) {
        addTappable(label, value, v -> PreferredNameActivity.openToEdit(this, role));
    }

    /** A row with a chevron that runs {@code onTap}. */
    private void addTappable(@StringRes int label, String value, View.OnClickListener onTap) {
        ItemInfoRowBinding row = addInfo(label, value);
        row.infoValue.setTextIsSelectable(false); // so a tap reaches the row
        row.infoChevron.setVisibility(View.VISIBLE);
        TypedValue ripple = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        row.getRoot().setBackgroundResource(ripple.resourceId);
        row.getRoot().setOnClickListener(onTap);
    }

    /** Bottom sheet with the +60 phone field, prefilled with the current number. */
    private void editPhone() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || profile == null) return;
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        SheetPhoneBinding s = SheetPhoneBinding.inflate(getLayoutInflater());
        sheet.setContentView(s.getRoot());

        String current = text("phone");
        if (current.startsWith("+60")) current = current.substring(3);
        s.inputPhone.setText(current);
        s.inputPhone.setSelection(current.length());

        s.btnSavePhone.setOnClickListener(v -> {
            String input = s.inputPhone.getText() == null ? "" : s.inputPhone.getText().toString();
            String error = Validators.phone(input);
            s.layoutPhone.setError(error);
            if (error != null) return;
            s.btnSavePhone.setEnabled(false);
            new UserRepository().setPhone(user.getUid(), role,
                    "+60" + Validators.phoneDigits(input), new Callback<Void>() {
                        @Override
                        public void onSuccess(Void unused) {
                            sheet.dismiss();
                            Snackbar.make(binding.getRoot(), R.string.phone_saved,
                                    Snackbar.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(String message) {
                            s.btnSavePhone.setEnabled(true);
                            s.layoutPhone.setError(message);
                        }
                    });
        });
        sheet.show();
    }

    /** A profile field as text; Db.text() also copes with numbers typed in the console. */
    private String text(String key) {
        String value = Db.text(profile.child(key));
        return value == null ? "" : value;
    }

    private void showError(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }
}
