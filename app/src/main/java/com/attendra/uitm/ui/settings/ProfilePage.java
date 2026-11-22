package com.attendra.uitm.ui.settings;

import android.content.Context;
import android.content.DialogInterface;
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
import com.attendra.uitm.data.TeachingJava;
import com.attendra.uitm.data.UserJava;
import com.attendra.uitm.databinding.ProfilePageBinding;
import com.attendra.uitm.databinding.ItemInfoRowBinding;
import com.attendra.uitm.databinding.SheetPhoneBinding;
import com.attendra.uitm.model.Club;
import com.attendra.uitm.model.Group;
import com.attendra.uitm.model.Programme;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.ui.BasePage;
import com.attendra.uitm.ui.common.Ui;
import com.attendra.uitm.ui.lecturer.LecturerMainPage;
import com.attendra.uitm.util.Formats;
import com.attendra.uitm.util.HomeRouter;
import com.attendra.uitm.util.Names;
import com.attendra.uitm.util.Validators;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** Profile Page. */
public class ProfilePage extends BasePage {

    private static final String EXTRA_ROLE = "role";

    /** Opens the Profile page. */
    public static void open(Context from, String role) {
        Intent intent = new Intent(from, ProfilePage.class);
        intent.putExtra(EXTRA_ROLE, role);
        from.startActivity(intent);
    }

    private ProfilePageBinding binding;
    private final Subscriptions subs = new Subscriptions();
    private String role;
    private DataSnapshot profile;
    private List<String> clubIds;
    private boolean seedReady;
    /** Lecturer only: classes this semester. */
    private Integer classCount;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ProfilePageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        padForSystemBars(binding.profileRoot);

        role = getIntent().getStringExtra(EXTRA_ROLE);
        boolean lecturer = Registration.ROLE_LECTURER.equals(role);
        binding.appbar.txtAppbarTitle.setText(R.string.profile_title);
        binding.appbar.btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        if (lecturer) {
            binding.txtRole.setText(R.string.home_role_lecturer);
            binding.txtClubsTitle.setText(R.string.label_clubs_lecturer);
        } else {
            binding.txtRole.setText(R.string.home_role_student);
            binding.txtClubsTitle.setText(R.string.label_clubs_student);
        }
        binding.cardClubs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MyClubsPage.open(ProfilePage.this, role);
            }
        });
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

        loadSeed();

        watchProfile(uid);

        watchClasses(uid);

        watchClubs(uid);
    }

    /** Loads club and programme names. */
    private void loadSeed() {
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
    }

    /** Follows the user's profile. */
    private void watchProfile(String uid) {
        subs.listen(UserJava.profileRef(uid, role), new Callback<DataSnapshot>() {
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
    }

    /** Lecturer only: follows the number of teaching classes. */
    private void watchClasses(String uid) {
        if (Registration.ROLE_LECTURER.equals(role)) {
            subs.listen(TeachingJava.ref(uid), new Callback<DataSnapshot>() {
                @Override
                public void onSuccess(DataSnapshot snapshot) {
                    classCount = TeachingJava.current(TeachingJava.parse(snapshot)).size();
                    render();
                }

                @Override
                public void onError(String message) {
                    showError(message);
                }
            });
        }
    }

    /** Follows the user's clubs. */
    private void watchClubs(String uid) {
        subs.listen(UserJava.clubsQuery(uid, role), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                clubIds = UserJava.clubIds(snapshot, uid, role);
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

    /** Draws the whole page again. */
    private void render() {
        renderInfo();
        renderClubs();
    }

    /** Name, avatar and the information rows. */
    private void renderInfo() {
        if (profile != null) {
            String name = text("name");
            binding.txtName.setText(name);
            binding.txtAvatar.setText(Names.initials(name));

            binding.listInfo.removeAllViews();
            // Editable rows: both open the Preferred Name Page.
            String preferred = text("preferredName");
            if (preferred.isEmpty()) {
                addEditable(R.string.profile_preferred_name, getString(R.string.profile_not_set));
            } else {
                addEditable(R.string.profile_preferred_name, preferred);
            }
            boolean lecturer = Registration.ROLE_LECTURER.equals(role);
            if (lecturer) {
                String title = text("title");
                if (title.isEmpty()) {
                    addEditable(R.string.profile_title_row, getString(R.string.name_title_none));
                } else {
                    addEditable(R.string.profile_title_row, title);
                }
                // Opens the Classes tab, where classes are added or changed.
                String classes;
                if (classCount == null) {
                    classes = getString(R.string.dash);
                } else {
                    classes = getResources().getQuantityString(R.plurals.classes_this_semester,
                            classCount, classCount);
                }
                addTappable(R.string.profile_teaching_classes, classes, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        LecturerMainPage.openClassesTab(ProfilePage.this);
                        finish();
                    }
                });
            }
            addInfo(R.string.profile_email, text("email"));
            addTappable(R.string.profile_phone, text("phone"), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    editPhone();
                }
            });
            if (lecturer) {
                addInfo(R.string.profile_staff_id, text("staffId"));
            } else {
                addStudentRows();
            }
        }
    }

    /** Student ID, programme, part, group and track. */
    private void addStudentRows() {
        addInfo(R.string.profile_student_id, text("studentId"));
        addInfo(R.string.profile_programme, text("programme"));
        Long part = profile.child("part").getValue(Long.class);
        if (part == null) {
            addInfo(R.string.profile_part, "");
        } else {
            addInfo(R.string.profile_part, String.valueOf(part));
        }
        addInfo(R.string.profile_group, text("groupId"));
        if (seedReady) {
            if (part == null) {
                addTrackRow(0);
            } else {
                addTrackRow(part.intValue());
            }
        }
    }

    /** One badge per club. */
    private void renderClubs() {
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
                    if (club != null) {
                        Ui.goldBadge(binding.rowClubs, club.shortName);
                    } else {
                        Ui.goldBadge(binding.rowClubs, id);
                    }
                }
            }
        }
    }

    /**
     * "My track", only once the programme splits into tracks (Part 5, or Part 4 for CS259). If the
     * group follows a track it's filled in and locked; otherwise the student picks one.
     */
    private void addTrackRow(int part) {
        Programme programme = SeedCache.programme(text("programme"));
        if (programme == null || !programme.hasTracksIn(part)) return;

        Group group = SeedCache.group(text("groupId"));
        if (group != null && group.track != null) {
            addInfo(R.string.profile_track, getString(R.string.profile_track_by_group,
                    programme.trackLabel(group.track)));
            return;
        }
        // The first pick is free; one change is allowed within a week of sign-up, then it's locked.
        String mine = text("track");
        Long createdAt = profile.child("createdAt").getValue(Long.class);
        long deadline;
        if (createdAt == null) {
            deadline = 0;
        } else {
            deadline = createdAt + UserJava.TRACK_CHANGE_WINDOW_MS;
        }
        if (mine.isEmpty()) {
            addTappable(R.string.profile_track, getString(R.string.profile_not_set),
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            pickTrack(programme, mine, deadline);
                        }
                    });
            return;
        }
        boolean canChange = !profile.child("trackChangedAt").exists()
                && System.currentTimeMillis() < deadline;
        if (canChange) {
            addTappable(R.string.profile_track, programme.trackLabel(mine),
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            pickTrack(programme, mine, deadline);
                        }
                    });
        } else {
            addInfo(R.string.profile_track, getString(R.string.profile_track_locked,
                    programme.trackLabel(mine)));
        }
    }

    /**
     * A simple list of the programme's tracks. A first pick inside the change window saves straight
     * away; anything that will lock the track asks first.
     */
    private void pickTrack(Programme programme, String current, long deadline) {
        List<String> ids = new ArrayList<>(new TreeMap<>(programme.tracks).keySet());
        String[] labels = new String[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            labels[i] = programme.trackLabel(ids.get(i));
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.track_pick_title)
                .setSingleChoiceItems(labels, ids.indexOf(current), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        String picked = ids.get(which);
                        if (picked.equals(current)) return;
                        boolean first = current.isEmpty();
                        if (first && System.currentTimeMillis() < deadline) {
                            saveTrack(picked, false);
                            return;
                        }
                        String label = programme.trackLabel(picked);
                        String body;
                        if (first) {
                            body = getString(R.string.track_final_body, label);
                        } else {
                            body = getString(R.string.track_change_body, label,
                                    Formats.shortDate(deadline));
                        }
                        new MaterialAlertDialogBuilder(ProfilePage.this)
                                .setTitle(R.string.track_change_title)
                                .setMessage(body)
                                .setNegativeButton(R.string.cancel, null)
                                .setPositiveButton(R.string.track_change_confirm,
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface d, int w) {
                                                saveTrack(picked, true);
                                            }
                                        })
                                .show();
                    }
                })
                .show();
    }

    /** Saves the chosen track. */
    private void saveTrack(String track, boolean isChange) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        new UserJava().setTrack(user.getUid(), track, isChange, new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                Snackbar.make(binding.getRoot(), R.string.track_saved,
                        Snackbar.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });
    }

    /** Adds one row to the information list. */
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
        addTappable(label, value, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PreferredNamePage.openToEdit(ProfilePage.this, role);
            }
        });
    }

    /** A row with a chevron that runs {@code onTap}. */
    private void addTappable(@StringRes int label, String value, View.OnClickListener onTap) {
        ItemInfoRowBinding row = addInfo(label, value);
        row.infoValue.setTextIsSelectable(false); // So a tap reaches the row
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
        if (current.startsWith("+60")) {
            current = current.substring(3);
        }
        s.inputPhone.setText(current);
        s.inputPhone.setSelection(current.length());

        s.btnSavePhone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String input;
                if (s.inputPhone.getText() == null) {
                    input = "";
                } else {
                    input = s.inputPhone.getText().toString();
                }
                String error = Validators.phone(input);
                s.layoutPhone.setError(error);
                if (error != null) return;
                s.btnSavePhone.setEnabled(false);
                new UserJava().setPhone(user.getUid(), role,
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
            }
        });
        sheet.show();
    }

    /** A profile field as text; Db.text() also copes with numbers typed in the console. */
    private String text(String key) {
        String value = Db.text(profile.child(key));
        if (value == null) {
            return "";
        }
        return value;
    }

    /** Shows an error message. */
    private void showError(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }
}
