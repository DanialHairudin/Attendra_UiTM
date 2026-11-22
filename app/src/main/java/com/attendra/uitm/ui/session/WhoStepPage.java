package com.attendra.uitm.ui.session;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.attendra.uitm.R;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.Db;
import com.attendra.uitm.data.SeedCache;
import com.attendra.uitm.data.Subscriptions;
import com.attendra.uitm.data.TestWorld;
import com.attendra.uitm.databinding.WhoStepPageBinding;
import com.attendra.uitm.databinding.ItemPickRowBinding;
import com.attendra.uitm.model.Club;
import com.attendra.uitm.model.Course;
import com.attendra.uitm.model.Session;
import com.attendra.uitm.ui.common.SessionText;
import com.attendra.uitm.ui.common.Ui;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.DataSnapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Step 2, Session Participants Page. */
public class WhoStepPage extends BaseStepPage {

    private WhoStepPageBinding binding;
    private final Subscriptions subs = new Subscriptions();
    /** groupId or clubId → number of students. */
    private final Map<String, Integer> counts = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = WhoStepPageBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        CreateSessionViewModel vm = vm();
        boolean club = vm.isType(Session.TYPE_CLUB);
        binding.txtTypeBadge.setText(SessionTypes.name(vm.type));
        if (club) {
            binding.txtTitleLabel.setText(R.string.who_activity_name);
        } else {
            binding.txtTitleLabel.setText(R.string.who_session_title);
        }
        binding.inputTitle.setText(vm.title);
        binding.inputTitle.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString();
                if (!text.equals(vm().title)) {
                    vm().title = text;
                    vm().titleEdited = !text.trim().isEmpty();
                    refreshContinue();
                }
            }
        });
        if (club) {
            binding.noteClub.setVisibility(View.VISIBLE);
        } else {
            binding.noteClub.setVisibility(View.GONE);
        }
        render();
    }

    @Override
    public void onStart() {
        super.onStart();
        CreateSessionViewModel vm = vm();
        if (vm.isType(Session.TYPE_CLUB)) {
            for (String c : vm.advisedClubs) {
                watchCount("clubMembers/", c);
            }
        } else {
            for (CreateSessionViewModel.Assignment a : vm.assignments) {
                watchCount("studentsByGroup/", a.groupId);
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        subs.clear();
        counts.clear();
    }

    /** Follows how many students a group or club has. */
    private void watchCount(String prefix, String id) {
        if (counts.containsKey(id)) return;
        subs.listen(Db.root().child(prefix + id), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                // Count only students in the lecturer's world (the Test Student is hidden).
                TestWorld.forLecturer(TestWorld.keys(snapshot), new Callback<Set<String>>() {
                    @Override
                    public void onSuccess(Set<String> uids) {
                        counts.put(id, uids.size());
                        render();
                    }

                    @Override
                    public void onError(String message) {
                        if (binding != null) {
                            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
                        }
                    }
                });
            }

            @Override
            public void onError(String message) {
                if (binding != null) {
                    Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
                }
            }
        });
    }

    // ------------------------------------------------------------------ render

    /** Draws the page again. */
    private void render() {
        if (binding == null) return;
        CreateSessionViewModel vm = vm();
        binding.listPick.removeAllViews();
        binding.listGroups.removeAllViews();
        binding.groupGroups.setVisibility(View.GONE);
        binding.txtSulamHint.setVisibility(View.GONE);

        if (vm.isType(Session.TYPE_CLUB)) {
            renderClubs();
        } else if (vm.isType(Session.TYPE_SULAM)) {
            renderSulam();
        } else if (vm.multiGroup()) {
            renderMultiGroup();
        } else {
            renderSingleClass();
        }
        renderSummary();
        refreshContinue();
    }

    /** Class: one row per assigned class; choose exactly one. */
    private void renderSingleClass() {
        CreateSessionViewModel vm = vm();
        binding.txtPickLabel.setText(R.string.who_class_label);
        if (showEmptyIf(vm.assignments.isEmpty(), R.drawable.ic_book,
                R.string.empty_classes_title, R.string.empty_classes_body)) {
            return;
        }
        for (CreateSessionViewModel.Assignment a : vm.assignments) {
            boolean on = a.courseId.equals(vm.courseId) && vm.groups.contains(a.groupId);
            ItemPickRowBinding row = addPick(binding.listPick, false, on,
                    courseCode(a.courseId) + " - " + a.groupId,
                    SessionText.join(courseName(a.courseId), studentsText(a.groupId)));
            row.getRoot().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    vm().courseId = a.courseId;
                    vm().groups.clear();
                    vm().groups.add(a.groupId);
                    suggestTitle(courseCode(a.courseId));
                    render();
                }
            });
        }
    }

    /**
     * SULAM: every Part 6 class the lecturer teaches, as one tick list (e.g. ICT652 - CDCS2406A and
     * ICT652 - CDCS2406B, if those are the classes assigned).
     */
    private void renderSulam() {
        CreateSessionViewModel vm = vm();
        binding.txtPickLabel.setText(R.string.who_groups_label_sulam);
        List<CreateSessionViewModel.Assignment> classes = vm.sulamClasses();
        if (showEmptyIf(classes.isEmpty(), R.drawable.ic_book,
                R.string.empty_sulam_title, R.string.empty_sulam_body)) {
            return;
        }

        // First visit: if all the classes share one course, tick them all.
        if (vm.courseId == null) {
            String only = classes.get(0).courseId;
            boolean oneCourse = true;
            for (CreateSessionViewModel.Assignment a : classes) {
                if (!a.courseId.equals(only)) {
                    oneCourse = false;
                }
            }
            if (oneCourse) {
                vm.courseId = only;
                for (CreateSessionViewModel.Assignment a : classes) {
                    vm.groups.add(a.groupId);
                }
                suggestTitle(courseCode(only));
            }
        }

        for (CreateSessionViewModel.Assignment a : classes) {
            boolean on = a.courseId.equals(vm.courseId) && vm.groups.contains(a.groupId);
            ItemPickRowBinding row = addPick(binding.listPick, true, on,
                    courseCode(a.courseId) + " - " + a.groupId,
                    SessionText.join(courseName(a.courseId), studentsText(a.groupId)));
            row.getRoot().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    CreateSessionViewModel m = vm();
                    if (!a.courseId.equals(m.courseId)) {
                        m.courseId = a.courseId;
                        m.groups.clear();
                        m.groups.add(a.groupId);
                        suggestTitle(courseCode(a.courseId));
                    } else if (!m.groups.remove(a.groupId)) {
                        m.groups.add(a.groupId);
                    }
                    if (m.groups.isEmpty()) {
                        m.courseId = null;
                    }
                    render();
                }
            });
        }
        showMissingSulamGroups(classes);
    }

    /**
     * SULAM groups of the same part that are not in the lecturer's Teaching Classes cannot be
     * picked (the rules only allow assigned groups), so say how to include them: "Add CDCS2406C in
     * Teaching Classes to include it." Uses the chosen course, or the first SULAM course when none
     * is chosen.
     */
    private void showMissingSulamGroups(List<CreateSessionViewModel.Assignment> classes) {
        String course;
        if (vm().courseId != null) {
            course = vm().courseId;
        } else {
            course = classes.get(0).courseId;
        }
        List<String> missing = vm().missingGroupsFor(course);
        if (missing.isEmpty()) return;
        binding.txtSulamHint.setText(getResources().getQuantityString(R.plurals.sulam_missing_groups,
                missing.size(), TextUtils.join(", ", missing)));
        binding.txtSulamHint.setVisibility(View.VISIBLE);
    }

    /** Test: one row per course (choose one), then that course's groups (tick several). */
    private void renderMultiGroup() {
        CreateSessionViewModel vm = vm();
        binding.txtPickLabel.setText(R.string.who_course_label);
        binding.txtGroupsLabel.setText(R.string.who_groups_label);
        List<String> courses = vm.courses();
        if (showEmptyIf(courses.isEmpty(), R.drawable.ic_book,
                R.string.empty_classes_title, R.string.empty_classes_body)) {
            return;
        }
        for (String c : courses) {
            int n = vm.groupsFor(c).size();
            ItemPickRowBinding row = addPick(binding.listPick, false, c.equals(vm.courseId),
                    courseCode(c), SessionText.join(courseName(c),
                            getResources().getQuantityString(R.plurals.groups_count, n, n)));
            row.getRoot().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!c.equals(vm().courseId)) {
                        vm().courseId = c;
                        vm().groups.clear();
                        // Start with every group ticked: a combined test or a combined SULAM activity
                        // (CDCS2406A, B and C together) is the common case.
                        vm().groups.addAll(vm().groupsFor(c));
                    }
                    suggestTitle(courseCode(c));
                    render();
                }
            });
        }
        if (vm.courseId == null) return;

        binding.groupGroups.setVisibility(View.VISIBLE);
        for (String g : vm.groupsFor(vm.courseId)) {
            ItemPickRowBinding row = addPick(binding.listGroups, true, vm.groups.contains(g),
                    g, studentsText(g));
            row.getRoot().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!vm().groups.remove(g)) {
                        vm().groups.add(g);
                    }
                    render();
                }
            });
        }
    }

    /** Club: one row per advised club; choose one. */
    private void renderClubs() {
        CreateSessionViewModel vm = vm();
        binding.txtPickLabel.setText(R.string.who_club_label);
        if (showEmptyIf(vm.advisedClubs.isEmpty(), R.drawable.ic_star,
                R.string.empty_advised_title, R.string.who_no_clubs_body)) {
            return;
        }
        for (String id : vm.advisedClubs) {
            Club club = SeedCache.club(id);
            String shortName = id;
            String fullName = "";
            if (club != null) {
                shortName = club.shortName;
                fullName = club.name;
            }
            final String clubTitle = shortName;
            ItemPickRowBinding row = addPick(binding.listPick, false, id.equals(vm.clubId),
                    clubTitle, fullName);
            Integer n = counts.get(id);
            row.pickBadge.setVisibility(View.VISIBLE);
            if (n == null) {
                row.pickBadge.setText(getString(R.string.dash));
            } else {
                row.pickBadge.setText(getResources().getQuantityString(R.plurals.members_count, n,
                        n));
            }
            row.getRoot().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    vm().clubId = id;
                    suggestTitle(clubTitle);
                    render();
                }
            });
        }
    }

    /** "CDCS2405A - 32 students", "3 groups - 96 students" or "ITEC - 48 members". */
    private void renderSummary() {
        CreateSessionViewModel vm = vm();
        String text = null;
        if (vm.isType(Session.TYPE_CLUB)) {
            if (vm.clubId != null) {
                Club club = SeedCache.club(vm.clubId);
                Integer n = counts.get(vm.clubId);
                String clubTitle;
                if (club != null) {
                    clubTitle = club.shortName;
                } else {
                    clubTitle = vm.clubId;
                }
                String members = null;
                vm.eligibleCount = 0;
                if (n != null) {
                    vm.eligibleCount = n;
                    members = getResources().getQuantityString(R.plurals.members_count, n, n);
                }
                text = SessionText.join(clubTitle, members);
            }
        } else if (!vm.groups.isEmpty()) {
            int total = 0;
            boolean known = true;
            for (String g : vm.groups) {
                Integer n = counts.get(g);
                if (n == null) {
                    known = false;
                } else {
                    total += n;
                }
            }
            vm.eligibleCount = total;
            String students;
            if (known) {
                students = getResources().getQuantityString(R.plurals.students_count, total, total);
            } else {
                students = null;
            }
            int groupCount = vm.groups.size();
            String who;
            if (groupCount == 1) {
                who = vm.groups.first();
            } else {
                who = getResources().getQuantityString(R.plurals.groups_count, groupCount,
                        groupCount);
            }
            text = SessionText.join(who, students);
        }
        if (text == null) {
            binding.txtSummary.setVisibility(View.GONE);
        } else {
            binding.txtSummary.setVisibility(View.VISIBLE);
        }
        binding.txtSummary.setText(text);
    }

    // ----------------------------------------------------------------- helpers

    /** Shows the empty message if there is nothing to pick. */
    private boolean showEmptyIf(boolean empty, int icon, int title, int body) {
        if (empty) {
            binding.emptyPick.getRoot().setVisibility(View.VISIBLE);
        } else {
            binding.emptyPick.getRoot().setVisibility(View.GONE);
        }
        if (empty) {
            Ui.setEmpty(binding.emptyPick, icon, title, body);
        }
        return empty;
    }

    /** Adds one row to pick. */
    private ItemPickRowBinding addPick(LinearLayout list, boolean multi, boolean on,
                                       String title, String meta) {
        ItemPickRowBinding row = ItemPickRowBinding.inflate(getLayoutInflater(), list, false);
        if (multi) {
            row.pickRadio.setVisibility(View.GONE);
            row.pickCheck.setVisibility(View.VISIBLE);
        } else {
            row.pickRadio.setVisibility(View.VISIBLE);
            row.pickCheck.setVisibility(View.GONE);
        }
        row.pickRadio.setChecked(on);
        row.pickCheck.setChecked(on);
        row.pickTitle.setText(title);
        row.pickMeta.setText(meta);
        row.getRoot().setSelected(on);
        // TalkBack reads e.g. "ICT602 - CDCS2405A, selected".
        if (on) {
            row.getRoot().setContentDescription(title + ", " + meta + ", " + getString(
                    R.string.selected));
        } else {
            row.getRoot().setContentDescription(title + ", " + meta + ", " + getString(
                    R.string.not_selected));
        }
        Ui.addRow(list, row.getRoot());
        return row;
    }

    /** Fills in a title like "ICT602 class" until the lecturer types their own. */
    private void suggestTitle(String what) {
        if (vm().titleEdited) return;
        String suggestion = getString(SessionTypes.titlePattern(vm().type), what);
        vm().title = suggestion;
        binding.inputTitle.setText(suggestion);
        vm().titleEdited = false; // Still a suggestion, not the lecturer's own text
    }

    /** "32 students", or a dash while loading. */
    private String studentsText(String groupId) {
        Integer n = counts.get(groupId);
        if (n == null) {
            return getString(R.string.dash);
        }
        return getResources().getQuantityString(R.plurals.students_count, n, n);
    }

    /** The course code, or its name if it has no code. */
    private static String courseCode(String courseId) {
        Course c = SeedCache.course(courseId);
        if (c == null) return courseId;
        if (c.hasRealCode()) {
            return c.code;
        }
        return c.name;
    }

    /** The course name, or null if the code is missing. */
    private static String courseName(String courseId) {
        Course c = SeedCache.course(courseId);
        if (c == null || !c.hasRealCode()) {
            return null;
        }
        return c.name;
    }

    @Override
    public int stepNumber() {
        return 2;
    }

    @Override
    public int continueLabel() {
        return R.string.continue_label;
    }

    @Override
    protected boolean canContinue() {
        return vm().whoValid();
    }

    @Override
    public void onContinue() {
        if (vm().title.trim().isEmpty()) {
            binding.layoutTitle.setError(getString(R.string.who_title_required));
            return;
        }
        binding.layoutTitle.setError(null);
        if (canContinue()) {
            host().showStep(new WhereStepPage());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
