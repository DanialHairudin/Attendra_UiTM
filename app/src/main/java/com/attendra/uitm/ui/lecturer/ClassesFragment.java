package com.attendra.uitm.ui.lecturer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.attendra.uitm.R;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.Db;
import com.attendra.uitm.data.SeedCache;
import com.attendra.uitm.data.Subscriptions;
import com.attendra.uitm.data.UserRepository;
import com.attendra.uitm.databinding.FragmentClassesBinding;
import com.attendra.uitm.databinding.ItemClassCardBinding;
import com.attendra.uitm.databinding.ItemClubRowBinding;
import com.attendra.uitm.model.Club;
import com.attendra.uitm.model.Course;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.ui.common.Ui;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lecturer Classes tab (CLAUDE.md 12.5). Read-only:
 * - teachingAssignments/{uid}     → the classes (set by the faculty office in the console)
 * - studentsByGroup/{groupId}     → number of students in each class
 * - clubAdvisors                  → the clubs this lecturer advises
 * - clubMembers/{clubId}          → number of members per club
 * All listeners are added in onStart and removed in onStop.
 */
public class ClassesFragment extends Fragment {

    /** One row of teachingAssignments/{uid}. */
    private static final class Assignment {
        String courseId;
        String groupId;
    }

    private FragmentClassesBinding binding;
    private final Subscriptions subs = new Subscriptions();

    private List<Assignment> assignments; // null = not loaded yet
    private List<String> clubIds;         // null = not loaded yet
    /** groupId or clubId → count. Missing = not loaded yet. */
    private final Map<String, Integer> groupCounts = new HashMap<>();
    private final Map<String, Integer> clubCounts = new HashMap<>();
    private final Map<String, Subscriptions.Handle> countHandles = new HashMap<>();
    private boolean seedReady;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentClassesBinding.inflate(inflater, container, false);
        Ui.setEmpty(binding.emptyClasses, R.drawable.ic_book,
                R.string.empty_classes_title, R.string.empty_classes_body);
        Ui.setEmpty(binding.emptyClubs, R.drawable.ic_star,
                R.string.empty_advised_title, R.string.empty_advised_body);
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

        subs.listen(Db.root().child("teachingAssignments").child(uid), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                List<Assignment> list = new ArrayList<>();
                for (DataSnapshot a : snapshot.getChildren()) {
                    Assignment item = new Assignment();
                    item.courseId = a.child("courseId").getValue(String.class);
                    item.groupId = a.child("groupId").getValue(String.class);
                    if (item.courseId == null || item.groupId == null) continue;
                    list.add(item);
                    watchCount("studentsByGroup/", item.groupId, groupCounts);
                }
                assignments = list;
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
        subs.listen(UserRepository.clubsQuery(uid, role), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                clubIds = UserRepository.clubIds(snapshot, uid, role);
                for (String id : clubIds) watchCount("clubMembers/", id, clubCounts);
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
                into.put(id, (int) snapshot.getChildrenCount());
                render();
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        }));
    }

    private void render() {
        if (binding == null || !seedReady) return;
        renderClasses();
        renderClubs();
    }

    private void renderClasses() {
        binding.listClasses.removeAllViews();
        if (assignments == null || assignments.isEmpty()) {
            binding.cardClassesState.setVisibility(View.VISIBLE);
            Ui.showListState(assignments == null ? Ui.ListState.LOADING : Ui.ListState.EMPTY,
                    binding.txtClassesLoading, binding.emptyClasses, binding.listClasses);
            return;
        }
        binding.cardClassesState.setVisibility(View.GONE);
        binding.listClasses.setVisibility(View.VISIBLE);

        List<Assignment> sorted = new ArrayList<>(assignments);
        Collections.sort(sorted, (a, b) -> {
            int byCourse = a.courseId.compareTo(b.courseId);
            return byCourse != 0 ? byCourse : a.groupId.compareTo(b.groupId);
        });
        for (Assignment a : sorted) {
            Course course = SeedCache.course(a.courseId);
            ItemClassCardBinding card = ItemClassCardBinding.inflate(getLayoutInflater(),
                    binding.listClasses, false);
            if (course != null && course.hasRealCode()) {
                card.classCode.setText(course.code);
            } else {
                card.classCode.setVisibility(View.GONE); // "TBC": show the name only
            }
            card.className.setText(course != null ? course.name : a.courseId);
            card.classGroup.setText(getString(R.string.group_label, a.groupId));
            Integer count = groupCounts.get(a.groupId);
            card.classCount.setText(count == null ? getString(R.string.dash)
                    : getResources().getQuantityString(R.plurals.students_count, count, count));
            card.getRoot().setOnClickListener(v ->
                    ClassDetailActivity.open(requireContext(), a.courseId, a.groupId));
            binding.listClasses.addView(card.getRoot());
        }
    }

    private void renderClubs() {
        binding.listClubs.removeAllViews();
        if (clubIds == null || clubIds.isEmpty()) {
            Ui.showListState(clubIds == null ? Ui.ListState.LOADING : Ui.ListState.EMPTY,
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
            Integer count = clubCounts.get(id);
            row.clubBadge.setVisibility(View.VISIBLE);
            row.clubBadge.setText(count == null ? getString(R.string.dash)
                    : getResources().getQuantityString(R.plurals.members_count, count, count));
            Ui.addRow(binding.listClubs, row.getRoot());
        }
    }

    private void showError(String message) {
        if (binding != null) Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
