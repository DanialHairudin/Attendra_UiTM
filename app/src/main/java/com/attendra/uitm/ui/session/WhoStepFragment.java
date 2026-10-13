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
import com.attendra.uitm.databinding.FragmentStepWhoBinding;
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
public class WhoStepFragment extends BaseStepFragment {

    private FragmentStepWhoBinding binding;
    private final Subscriptions subs = new Subscriptions();
    /** groupId or clubId → number of students. */
    private final Map<String, Integer> counts = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentStepWhoBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        CreateSessionViewModel vm = vm();
        boolean club = vm.isType(Session.TYPE_CLUB);
        binding.txtTypeBadge.setText(SessionTypes.name(vm.type));
        binding.txtTitleLabel.setText(club ? R.string.who_activity_name : R.string.who_session_title);
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
        binding.noteClub.setVisibility(club ? View.VISIBLE : View.GONE);
        render();
    }

    @Override
    public void onStart() {
        super.onStart();
        CreateSessionViewModel vm = vm();
        if (vm.isType(Session.TYPE_CLUB)) {
            for (String c : vm.advisedClubs) watchCount("clubMembers/", c);
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
                R.string.empty_classes_title, R.string.empty_classes_body)) return;
        for (CreateSessionViewModel.Assignment a : vm.assignments) {
            boolean on = a.courseId.equals(vm.courseId) && vm.groups.contains(a.groupId);
            ItemPickRowBinding row = addPick(binding.listPick, false, on,
                    courseCode(a.courseId) + " · " + a.groupId,
                    SessionText.join(courseName(a.courseId), studentsText(a.groupId)));
            row.getRoot().setOnClickListener(v -> {
                vm().courseId = a.courseId;
                vm().groups.clear();
                vm().groups.add(a.groupId);
                suggestTitle(courseCode(a.courseId));
                render();
            });
        }
    }

    /**
     * SULAM: every Part 6 class the lecturer teaches, as one tick list (e.g. ICT652 · CDCS2406A and
     * ICT652 · CDCS2406B, if those are the classes assigned).
     */
    private void renderSulam() {
        CreateSessionViewModel vm = vm();
        binding.txtPickLabel.setText(R.string.who_groups_label_sulam);
        List<CreateSessionViewModel.Assignment> classes = vm.sulamClasses();
        if (showEmptyIf(classes.isEmpty(), R.drawable.ic_book,
                R.string.empty_sulam_title, R.string.empty_sulam_body)) return;

        // First visit: if all the classes share one course, tick them all.
        if (vm.courseId == null) {
            String only = classes.get(0).courseId;
            boolean oneCourse = true;
            for (CreateSessionViewModel.Assignment a : classes) {
                if (!a.courseId.equals(only)) oneCourse = false;
            }
            if (oneCourse) {
                vm.courseId = only;
                for (CreateSessionViewModel.Assignment a : classes) vm.groups.add(a.groupId);
                suggestTitle(courseCode(only));
            }
        }

        for (CreateSessionViewModel.Assignment a : classes) {
            boolean on = a.courseId.equals(vm.courseId) && vm.groups.contains(a.groupId);
            ItemPickRowBinding row = addPick(binding.listPick, true, on,
                    courseCode(a.courseId) + " · " + a.groupId,
                    SessionText.join(courseName(a.courseId), studentsText(a.groupId)));
            row.getRoot().setOnClickListener(v -> {
                CreateSessionViewModel m = vm();
                if (!a.courseId.equals(m.courseId)) {
                    m.courseId = a.courseId;
                    m.groups.clear();
                    m.groups.add(a.groupId);
                    suggestTitle(courseCode(a.courseId));
                } else if (!m.groups.remove(a.groupId)) {
                    m.groups.add(a.groupId);
                }
                if (m.groups.isEmpty()) m.courseId = null;
                render();
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
        String course = vm().courseId != null ? vm().courseId : classes.get(0).courseId;
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
                R.string.empty_classes_title, R.string.empty_classes_body)) return;
        for (String c : courses) {
            int n = vm.groupsFor(c).size();
            ItemPickRowBinding row = addPick(binding.listPick, false, c.equals(vm.courseId),
                    courseCode(c), SessionText.join(courseName(c),
                            getResources().getQuantityString(R.plurals.groups_count, n, n)));
            row.getRoot().setOnClickListener(v -> {
                if (!c.equals(vm().courseId)) {
                    vm().courseId = c;
                    vm().groups.clear();
                    // Start with every group ticked: a combined test or a combined SULAM activity
                    // (CDCS2406A, B and C together) is the common case.
                    vm().groups.addAll(vm().groupsFor(c));
                }
                suggestTitle(courseCode(c));
                render();
            });
        }
        if (vm.courseId == null) return;

        binding.groupGroups.setVisibility(View.VISIBLE);
        for (String g : vm.groupsFor(vm.courseId)) {
            ItemPickRowBinding row = addPick(binding.listGroups, true, vm.groups.contains(g),
                    g, studentsText(g));
            row.getRoot().setOnClickListener(v -> {
                if (!vm().groups.remove(g)) vm().groups.add(g);
                render();
            });
        }
    }

    /** Club: one row per advised club; choose one. */
    private void renderClubs() {
        CreateSessionViewModel vm = vm();
        binding.txtPickLabel.setText(R.string.who_club_label);
        if (showEmptyIf(vm.advisedClubs.isEmpty(), R.drawable.ic_star,
                R.string.empty_advised_title, R.string.who_no_clubs_body)) return;
        for (String id : vm.advisedClubs) {
            Club club = SeedCache.club(id);
            ItemPickRowBinding row = addPick(binding.listPick, false, id.equals(vm.clubId),
                    club != null ? club.shortName : id, club != null ? club.name : "");
            Integer n = counts.get(id);
            row.pickBadge.setVisibility(View.VISIBLE);
            row.pickBadge.setText(n == null ? getString(R.string.dash)
                    : getResources().getQuantityString(R.plurals.members_count, n, n));
            row.getRoot().setOnClickListener(v -> {
                vm().clubId = id;
                suggestTitle(club != null ? club.shortName : id);
                render();
            });
        }
    }

    /** "CDCS2405A · 32 students", "3 groups · 96 students" or "ITEC · 48 members". */
    private void renderSummary() {
        CreateSessionViewModel vm = vm();
        String text = null;
        if (vm.isType(Session.TYPE_CLUB)) {
            if (vm.clubId != null) {
                Club club = SeedCache.club(vm.clubId);
                Integer n = counts.get(vm.clubId);
                vm.eligibleCount = n == null ? 0 : n;
                text = SessionText.join(club != null ? club.shortName : vm.clubId,
                        n == null ? null
                                : getResources().getQuantityString(R.plurals.members_count, n, n));
            }
        } else if (!vm.groups.isEmpty()) {
            int total = 0;
            boolean known = true;
            for (String g : vm.groups) {
                Integer n = counts.get(g);
                if (n == null) known = false; else total += n;
            }
            vm.eligibleCount = total;
            String students = known
                    ? getResources().getQuantityString(R.plurals.students_count, total, total) : null;
            String who = vm.groups.size() == 1 ? vm.groups.first()
                    : getResources().getQuantityString(R.plurals.groups_count,
                    vm.groups.size(), vm.groups.size());
            text = SessionText.join(who, students);
        }
        binding.txtSummary.setVisibility(text == null ? View.GONE : View.VISIBLE);
        binding.txtSummary.setText(text);
    }

    // ----------------------------------------------------------------- helpers

    private boolean showEmptyIf(boolean empty, int icon, int title, int body) {
        binding.emptyPick.getRoot().setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) Ui.setEmpty(binding.emptyPick, icon, title, body);
        return empty;
    }

    private ItemPickRowBinding addPick(LinearLayout list, boolean multi, boolean on,
                                       String title, String meta) {
        ItemPickRowBinding row = ItemPickRowBinding.inflate(getLayoutInflater(), list, false);
        row.pickRadio.setVisibility(multi ? View.GONE : View.VISIBLE);
        row.pickCheck.setVisibility(multi ? View.VISIBLE : View.GONE);
        row.pickRadio.setChecked(on);
        row.pickCheck.setChecked(on);
        row.pickTitle.setText(title);
        row.pickMeta.setText(meta);
        row.getRoot().setSelected(on);
        // TalkBack reads e.g. "ICT602 · CDCS2405A, selected".
        row.getRoot().setContentDescription(title + ", " + meta + ", "
                + getString(on ? R.string.selected : R.string.not_selected));
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

    private String studentsText(String groupId) {
        Integer n = counts.get(groupId);
        return n == null ? getString(R.string.dash)
                : getResources().getQuantityString(R.plurals.students_count, n, n);
    }

    private static String courseCode(String courseId) {
        Course c = SeedCache.course(courseId);
        if (c == null) return courseId;
        return c.hasRealCode() ? c.code : c.name;
    }

    private static String courseName(String courseId) {
        Course c = SeedCache.course(courseId);
        return c == null || !c.hasRealCode() ? null : c.name;
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
        if (canContinue()) host().showStep(new WhereStepFragment());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
