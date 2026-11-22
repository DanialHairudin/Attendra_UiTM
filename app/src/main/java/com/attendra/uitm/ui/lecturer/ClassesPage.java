package com.attendra.uitm.ui.lecturer;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.attendra.uitm.R;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.Db;
import com.attendra.uitm.data.SeedCache;
import com.attendra.uitm.data.Subscriptions;
import com.attendra.uitm.data.TeachingJava;
import com.attendra.uitm.data.TestWorld;
import com.attendra.uitm.data.UserJava;
import com.attendra.uitm.databinding.ClassesPageBinding;
import com.attendra.uitm.databinding.ItemClassCardBinding;
import com.attendra.uitm.databinding.ItemClubRowBinding;
import com.attendra.uitm.model.Club;
import com.attendra.uitm.model.Course;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.model.TeachingAssignment;
import com.attendra.uitm.ui.common.Ui;
import com.attendra.uitm.util.SemesterUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lecturer Classes tab. */
public class ClassesPage extends Fragment {

    private ClassesPageBinding binding;
    private final Subscriptions subs = new Subscriptions();

    private List<TeachingAssignment> assignments; // Null = not loaded yet
    private boolean showPrevious;
    private List<String> clubIds;         // Null = not loaded yet
    /** groupId or clubId → count. */
    private final Map<String, Integer> groupCounts = new HashMap<>();
    private final Map<String, Integer> clubCounts = new HashMap<>();
    private final Map<String, Subscriptions.Handle> countHandles = new HashMap<>();
    private boolean seedReady;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = ClassesPageBinding.inflate(inflater, container, false);
        Ui.setEmpty(binding.emptyClasses, R.drawable.ic_book,
                R.string.empty_classes_title, R.string.empty_classes_body);
        Ui.setEmpty(binding.emptyClubs, R.drawable.ic_star,
                R.string.empty_advised_title, R.string.empty_advised_body);
        binding.txtSemester.setText(getString(R.string.teach_semester_line,
                SemesterUtil.currentSemester()));
        binding.btnAddClass.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AddTeachingClassPage.open(requireContext());
            }
        });
        binding.btnTogglePrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPrevious = !showPrevious;
                render();
            }
        });
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
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

        subs.listen(TeachingJava.ref(uid), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                assignments = TeachingJava.parse(snapshot);
                for (TeachingAssignment a : assignments) {
                    watchCount("studentsByGroup/", a.groupId, groupCounts);
                }
                render();
            }

            @Override
            public void onError(String message) {
                assignments = new ArrayList<>();
                showError(message);
                render();
            }
        });

        String role = Registration.ROLE_LECTURER;
        subs.listen(UserJava.clubsQuery(uid, role), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                clubIds = UserJava.clubIds(snapshot, uid, role);
                for (String id : clubIds) {
                    watchCount("clubMembers/", id, clubCounts);
                }
                render();
            }

            @Override
            public void onError(String message) {
                clubIds = new ArrayList<>();
                showError(message);
                render();
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        subs.clear();
        countHandles.clear();
        groupCounts.clear();
        clubCounts.clear();
        assignments = null;
        clubIds = null;
    }

    /** Live count of the children of {prefix}{id}, e.g. studentsByGroup/CDCS2405A. */
    private void watchCount(String prefix, String id, Map<String, Integer> into) {
        String path = prefix + id;
        if (countHandles.containsKey(path)) return;
        countHandles.put(path, subs.listen(Db.root().child(path), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                // Count only students in the lecturer's world (the Test Student is hidden).
                TestWorld.forLecturer(TestWorld.keys(snapshot), new Callback<Set<String>>() {
                    @Override
                    public void onSuccess(Set<String> uids) {
                        if (!countHandles.containsKey(path)) return; // stopped
                        into.put(id, uids.size());
                        render();
                    }

                    @Override
                    public void onError(String message) {
                        showError(message);
                    }
                });
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        }));
    }

    /** Draws the whole page again. */
    private void render() {
        if (binding == null || !seedReady) return;
        renderClasses();
        renderClubs();
    }

    /** One card per teaching class. */
    private void renderClasses() {
        binding.listClasses.removeAllViews();
        binding.listPrevious.removeAllViews();
        // Main list: this semester (and the next one, if already added).
        List<TeachingAssignment> active = new ArrayList<>();
        List<TeachingAssignment> previous = new ArrayList<>();
        if (assignments != null) {
            for (TeachingAssignment a : assignments) {
                if (SemesterUtil.isPast(a.semester)) {
                    previous.add(a);
                } else {
                    active.add(a);
                }
            }
        }
        if (previous.isEmpty()) {
            binding.btnTogglePrevious.setVisibility(View.GONE);
        } else {
            binding.btnTogglePrevious.setVisibility(View.VISIBLE);
        }
        if (showPrevious) {
            binding.btnTogglePrevious.setText(R.string.teach_hide_previous);
        } else {
            binding.btnTogglePrevious.setText(R.string.teach_show_previous);
        }
        if (showPrevious && !previous.isEmpty()) {
            binding.listPrevious.setVisibility(View.VISIBLE);
        } else {
            binding.listPrevious.setVisibility(View.GONE);
        }
        for (TeachingAssignment a : previous) {
            addClassCard(binding.listPrevious, a, false);
        }

        if (active.isEmpty()) {
            binding.cardClassesState.setVisibility(View.VISIBLE);
            if (assignments == null) {
                Ui.showListState(Ui.ListState.LOADING, binding.txtClassesLoading,
                        binding.emptyClasses, binding.listClasses);
            } else {
                Ui.showListState(Ui.ListState.EMPTY, binding.txtClassesLoading, binding.emptyClasses,
                        binding.listClasses);
            }
            return;
        }
        binding.cardClassesState.setVisibility(View.GONE);
        binding.listClasses.setVisibility(View.VISIBLE);
        for (TeachingAssignment a : active) {
            addClassCard(binding.listClasses, a, true);
        }
    }

    /** One class card. */
    private void addClassCard(LinearLayout list, TeachingAssignment a, boolean editable) {
        Course course = SeedCache.course(a.courseId);
        ItemClassCardBinding card = ItemClassCardBinding.inflate(getLayoutInflater(), list, false);
        if (course != null && course.hasRealCode()) {
            card.classCode.setText(course.code);
        } else {
            card.classCode.setVisibility(View.GONE); // "TBC": show the name only
        }
        if (course != null) {
            card.className.setText(course.name);
        } else {
            card.className.setText(a.courseId);
        }
        card.classGroup.setText(getString(R.string.group_label, a.groupId));
        if (!SemesterUtil.isCurrent(a.semester)) {
            card.classSemester.setVisibility(View.VISIBLE);
            card.classSemester.setText(a.semester);
        }
        Integer count = groupCounts.get(a.groupId);
        if (count == null) {
            card.classCount.setText(getString(R.string.dash));
        } else {
            card.classCount.setText(getResources().getQuantityString(R.plurals.students_count, count,
                    count));
        }
        card.getRoot().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClassDetailPage.open(requireContext(), a.courseId, a.groupId);
            }
        });
        if (editable) {
            card.btnClassEdit.setVisibility(View.VISIBLE);
        } else {
            card.btnClassEdit.setVisibility(View.GONE);
        }
        card.btnClassEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AddTeachingClassPage.openToEdit(requireContext(), a);
            }
        });
        card.btnClassSummary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SemesterSummaryPage.open(requireContext(), a.courseId, a.groupId, a.semester);
            }
        });
        card.btnClassRemove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmRemove(a, course);
            }
        });
        list.addView(card.getRoot());
    }

    /** "Remove ICT600 · CDCS2405A from your teaching classes? */
    private void confirmRemove(TeachingAssignment a, @Nullable Course course) {
        String name;
        if (course == null) {
            name = a.courseId;
        } else if (course.hasRealCode()) {
            name = course.code;
        } else {
            name = course.name;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(getString(R.string.teach_remove_confirm, name, a.groupId))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.teach_remove, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String uid = FirebaseAuth.getInstance().getUid();
                        if (uid == null) return;
                        // Only the assignment goes; past sessions keep their own groups.
                        TeachingJava.remove(uid, a.key(), new Callback<Void>() {
                            @Override
                            public void onSuccess(Void unused) {
                                // The live listener redraws the list.
                            }

                            @Override
                            public void onError(String message) {
                                showError(message);
                            }
                        });
                    }
                })
                .show();
    }

    /** One card per club. */
    private void renderClubs() {
        binding.listClubs.removeAllViews();
        if (clubIds == null || clubIds.isEmpty()) {
            if (clubIds == null) {
                Ui.showListState(Ui.ListState.LOADING, binding.txtClubsLoading, binding.emptyClubs,
                        binding.listClubs);
            } else {
                Ui.showListState(Ui.ListState.EMPTY, binding.txtClubsLoading, binding.emptyClubs,
                        binding.listClubs);
            }
            return;
        }
        Ui.showListState(Ui.ListState.CONTENT, binding.txtClubsLoading, binding.emptyClubs,
                binding.listClubs);
        for (String id : clubIds) {
            Club club = SeedCache.club(id);
            ItemClubRowBinding row = ItemClubRowBinding.inflate(getLayoutInflater(),
                    binding.listClubs, false);
            if (club != null) {
                row.clubShort.setText(club.shortName);
            } else {
                row.clubShort.setText(id);
            }
            if (club != null) {
                row.clubName.setText(club.name);
            } else {
                row.clubName.setText("");
            }
            Integer count = clubCounts.get(id);
            row.clubBadge.setVisibility(View.VISIBLE);
            if (count == null) {
                row.clubBadge.setText(getString(R.string.dash));
            } else {
                row.clubBadge.setText(getResources().getQuantityString(R.plurals.members_count,
                        count, count));
            }
            Ui.addRow(binding.listClubs, row.getRoot());
        }
    }

    /** Shows an error message. */
    private void showError(String message) {
        if (binding != null) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
