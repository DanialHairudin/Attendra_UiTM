package com.attendra.uitm.ui.lecturer;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.attendra.uitm.R;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.Db;
import com.attendra.uitm.data.SeedCache;
import com.attendra.uitm.data.SessionWatcher;
import com.attendra.uitm.data.Subscriptions;
import com.attendra.uitm.data.TestWorld;
import com.attendra.uitm.databinding.ActivityClassDetailBinding;
import com.attendra.uitm.databinding.ItemPersonRowBinding;
import com.attendra.uitm.databinding.ItemSessionRowBinding;
import com.attendra.uitm.model.Course;
import com.attendra.uitm.model.Session;
import com.attendra.uitm.ui.BaseActivity;
import com.attendra.uitm.ui.common.SessionText;
import com.attendra.uitm.ui.common.Ui;
import com.attendra.uitm.util.Formats;
import com.attendra.uitm.util.HomeRouter;
import com.attendra.uitm.util.Names;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class Detail Page: one assigned class (course + group).
 * - Sessions: the lecturer's sessions for this course and group (often none yet).
 * - Students: everyone in studentsByGroup/{groupId}, with name and student ID
 *   from students/{uid}.
 * Listeners are added in onStart and removed in onStop.
 */
public class ClassDetailActivity extends BaseActivity {

    private static final String EXTRA_COURSE = "courseId";
    private static final String EXTRA_GROUP = "groupId";

    /** Name and ID of one student in the roster. */
    private static final class Student {
        String name;
        String studentId;
    }

    public static void open(Context from, String courseId, String groupId) {
        Intent intent = new Intent(from, ClassDetailActivity.class);
        intent.putExtra(EXTRA_COURSE, courseId);
        intent.putExtra(EXTRA_GROUP, groupId);
        from.startActivity(intent);
    }

    private ActivityClassDetailBinding binding;
    private String courseId;
    private String groupId;
    private final Subscriptions subs = new Subscriptions();
    private final Map<String, Subscriptions.Handle> studentHandles = new HashMap<>();
    private SessionWatcher watcher;

    private Map<String, Session> sessions = new HashMap<>();
    private boolean sessionsReady;
    /** uid → student; a null value means that student's profile is still loading. */
    private final Map<String, Student> roster = new LinkedHashMap<>();
    private boolean rosterLoaded;
    private boolean seedReady;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityClassDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        padForSystemBars(binding.detailRoot);

        courseId = getIntent().getStringExtra(EXTRA_COURSE);
        groupId = getIntent().getStringExtra(EXTRA_GROUP);
        binding.appbar.btnBack.setOnClickListener(v -> finish());
        binding.txtGroup.setText(groupId);
        Ui.setEmpty(binding.emptySessions, R.drawable.ic_clock,
                R.string.empty_class_sessions_title, R.string.empty_class_sessions_body);
        Ui.setEmpty(binding.emptyStudents, R.drawable.ic_book,
                R.string.empty_students_title, R.string.empty_students_body);
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            HomeRouter.openSplash(this);
            return;
        }

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

        watcher = new SessionWatcher(new SessionWatcher.Listener() {
            @Override
            public void onSessions(Map<String, Session> all, boolean ready) {
                sessions = all;
                sessionsReady = ready;
                render();
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });
        watcher.watchIndex("lecturer",
                Db.root().child("sessionsByLecturer").child(user.getUid()));

        subs.listen(Db.root().child("studentsByGroup").child(groupId), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                // Only students in the lecturer's world (the Test Student is hidden).
                TestWorld.forLecturer(TestWorld.keys(snapshot), new Callback<Set<String>>() {
                    @Override
                    public void onSuccess(Set<String> uids) {
                        syncRoster(new ArrayList<>(uids));
                        rosterLoaded = true;
                        render();
                    }

                    @Override
                    public void onError(String message) {
                        rosterLoaded = true;
                        showError(message);
                        render();
                    }
                });
            }

            @Override
            public void onError(String message) {
                rosterLoaded = true;
                showError(message);
                render();
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        subs.clear();
        studentHandles.clear();
        roster.clear();
        rosterLoaded = false;
        if (watcher != null) watcher.stop();
    }

    /** Listens to students/{uid} for each student in the group. */
    private void syncRoster(List<String> uids) {
        for (String uid : new ArrayList<>(studentHandles.keySet())) {
            if (!uids.contains(uid)) {
                studentHandles.remove(uid).remove();
                roster.remove(uid);
            }
        }
        for (String uid : uids) {
            if (studentHandles.containsKey(uid)) continue;
            roster.put(uid, null);
            studentHandles.put(uid, subs.listen(Db.root().child("students").child(uid),
                    new Callback<DataSnapshot>() {
                        @Override
                        public void onSuccess(DataSnapshot profile) {
                            Student s = new Student();
                            s.name = profile.child("name").getValue(String.class);
                            s.studentId = Db.text(profile.child("studentId"));
                            roster.put(uid, s);
                            render();
                        }

                        @Override
                        public void onError(String message) {
                            showError(message);
                        }
                    }));
        }
    }

    private void render() {
        if (binding == null || !seedReady) return;

        Course course = SeedCache.course(courseId);
        binding.appbar.txtAppbarTitle.setText(course != null && course.hasRealCode()
                ? course.code : getString(R.string.class_title_fallback));
        binding.txtCourseName.setText(course != null ? course.name : courseId);

        renderSessions();
        renderStudents();
    }

    private void renderSessions() {
        List<Session> mine = new ArrayList<>();
        int held = 0;
        for (Session s : sessions.values()) {
            if (courseId.equals(s.courseId) && s.groupIds().contains(groupId)) {
                mine.add(s);
                if (!s.isOpen()) held++;
            }
        }
        binding.txtSessionsHeld.setText(sessionsReady
                ? getResources().getQuantityString(R.plurals.sessions_held, held, held) : "");

        binding.listSessions.removeAllViews();
        if (mine.isEmpty()) {
            Ui.showListState(sessionsReady ? Ui.ListState.EMPTY : Ui.ListState.LOADING,
                    binding.txtSessionsLoading, binding.emptySessions, binding.listSessions);
            return;
        }
        Ui.showListState(Ui.ListState.CONTENT, binding.txtSessionsLoading, binding.emptySessions,
                binding.listSessions);
        Collections.sort(mine, (a, b) -> Long.compare(b.start, a.start)); // newest first
        long now = System.currentTimeMillis();
        for (Session s : mine) {
            ItemSessionRowBinding row = ItemSessionRowBinding.inflate(getLayoutInflater(),
                    binding.listSessions, false);
            row.rowIcon.setImageResource(SessionText.icon(s));
            row.rowTitle.setText(s.title);
            row.rowMeta.setText(SessionText.join(Formats.shortDate(s.start),
                    Formats.timeRange(s.start, s.end), SessionText.room(this, s)));
            SessionText.statusChip(row.rowChip, s, now);
            row.getRoot().setClickable(false);
            row.getRoot().setBackground(null);
            Ui.addRow(binding.listSessions, row.getRoot());
        }
    }

    private void renderStudents() {
        int count = roster.size();
        binding.txtStudentCount.setText(rosterLoaded
                ? getResources().getQuantityString(R.plurals.students_count, count, count) : "");

        binding.listStudents.removeAllViews();
        if (!rosterLoaded || count == 0) {
            Ui.showListState(rosterLoaded ? Ui.ListState.EMPTY : Ui.ListState.LOADING,
                    binding.txtStudentsLoading, binding.emptyStudents, binding.listStudents);
            return;
        }
        Ui.showListState(Ui.ListState.CONTENT, binding.txtStudentsLoading, binding.emptyStudents,
                binding.listStudents);

        List<Student> list = new ArrayList<>();
        for (Student s : roster.values()) if (s != null) list.add(s);
        Collections.sort(list, (a, b) -> String.valueOf(a.name)
                .compareToIgnoreCase(String.valueOf(b.name)));
        for (Student s : list) {
            ItemPersonRowBinding row = ItemPersonRowBinding.inflate(getLayoutInflater(),
                    binding.listStudents, false);
            String name = s.name == null ? "" : s.name;
            row.personAvatar.setText(Names.initials(name));
            row.personName.setText(name);
            row.personMeta.setText(SessionText.join(s.studentId, groupId));
            Ui.addRow(binding.listStudents, row.getRoot());
        }
    }

    private void showError(String message) {
        if (binding != null) Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }
}
