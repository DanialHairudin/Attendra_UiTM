package com.attendra.uitm.ui.student;

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
import com.attendra.uitm.databinding.FragmentMyAttendanceBinding;
import com.attendra.uitm.databinding.ItemCourseCardBinding;
import com.attendra.uitm.databinding.ItemSessionRowBinding;
import com.attendra.uitm.model.Attendance;
import com.attendra.uitm.model.Course;
import com.attendra.uitm.model.Session;
import com.attendra.uitm.ui.common.AttendeeText;
import com.attendra.uitm.ui.common.SessionText;
import com.attendra.uitm.ui.common.Ui;
import com.attendra.uitm.util.Formats;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * My Attendance Page (CLAUDE.md section 9, build step C10).
 *
 * Data: studentAttendance/{uid} (the student's own mirror: {status, time} per
 * session), live. Each session's title, course and time come from
 * sessions/{sid}, read once per session and kept.
 *
 * - Overall: attended (present + late) out of every recorded session.
 * - One card per course: percentage, bar, "19 of 20 sessions · 1 late".
 *   Club sessions are not a course, so they only appear in the history.
 * - History: every recorded session, newest first, with its status.
 */
public class MyAttendanceFragment extends Fragment {

    /** One studentAttendance entry. */
    private static final class Mark {
        final String status;
        final long time;

        Mark(String status, long time) {
            this.status = status;
            this.time = time;
        }
    }

    private FragmentMyAttendanceBinding binding;
    private final Subscriptions subs = new Subscriptions();
    /** sessionId → my mark. null until studentAttendance has loaded once. */
    @Nullable
    private Map<String, Mark> marks;
    /** sessionId → session (null value = the session was deleted). Kept between visits. */
    private final Map<String, Session> sessions = new HashMap<>();
    private final Set<String> lookups = new HashSet<>();
    private boolean seedReady;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMyAttendanceBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Ui.setEmpty(binding.empty, R.drawable.ic_chart,
                R.string.empty_courses_title, R.string.empty_courses_body);
    }

    @Override
    public void onStart() {
        super.onStart();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        SeedCache.load(new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                seedReady = true;
                render();
            }

            @Override
            public void onError(String message) {
                seedReady = true; // course names fall back to their IDs
                showError(message);
                render();
            }
        });

        subs.listen(Db.root().child("studentAttendance").child(user.getUid()),
                new Callback<DataSnapshot>() {
                    @Override
                    public void onSuccess(DataSnapshot snapshot) {
                        Map<String, Mark> map = new LinkedHashMap<>();
                        for (DataSnapshot rec : snapshot.getChildren()) {
                            String status = rec.child("status").getValue(String.class);
                            Long time = rec.child("time").getValue(Long.class);
                            if (status != null) {
                                map.put(rec.getKey(), new Mark(status, time == null ? 0 : time));
                                lookUpSession(rec.getKey());
                            }
                        }
                        marks = map;
                        render();
                    }

                    @Override
                    public void onError(String message) {
                        marks = new LinkedHashMap<>();
                        showError(message);
                        render();
                    }
                });
    }

    @Override
    public void onStop() {
        super.onStop();
        subs.clear();
    }

    /** Reads sessions/{sid} once for a session we have not seen yet. */
    private void lookUpSession(String sid) {
        if (sessions.containsKey(sid) || !lookups.add(sid)) return;
        Db.readOnce(Db.root().child("sessions").child(sid), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                Session s = snapshot.getValue(Session.class);
                if (s != null) s.id = sid;
                sessions.put(sid, s);
                render();
            }

            @Override
            public void onError(String message) {
                lookups.remove(sid); // try again on the next update
            }
        });
    }

    // ------------------------------------------------------------------ render

    private void render() {
        if (binding == null || !seedReady) return;
        Map<String, Mark> all = marks;
        boolean loading = all == null || !sessions.keySet().containsAll(all.keySet());
        binding.txtLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (all == null) return;

        renderOverall(all);
        boolean empty = all.isEmpty();
        binding.cardEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        renderCourses(all);
        renderHistory(all);
    }

    /** "92%" and the present / late / absent chips, out of every recorded session. */
    private void renderOverall(Map<String, Mark> all) {
        int present = 0;
        int late = 0;
        for (Mark m : all.values()) {
            if (Attendance.PRESENT.equals(m.status)) present++;
            else if (Attendance.LATE.equals(m.status)) late++;
        }
        int total = all.size();
        int absent = total - present - late;
        binding.txtOverallPct.setText(total == 0 ? getString(R.string.dash)
                : getString(R.string.percent, Formats.percent(present + late, total)));
        binding.chipPresent.setText(getString(R.string.n_present, present));
        binding.chipLate.setText(getString(R.string.n_late, late));
        binding.chipAbsent.setText(getString(R.string.n_absent, absent));
        binding.txtOfTotal.setText(getString(R.string.of_total, total));
    }

    /** One card per course: {attended, total, late}. Club sessions are skipped. */
    private void renderCourses(Map<String, Mark> all) {
        binding.listCourses.removeAllViews();
        Map<String, int[]> perCourse = new TreeMap<>();
        for (Map.Entry<String, Mark> e : all.entrySet()) {
            Session s = sessions.get(e.getKey());
            if (s == null || s.isClub() || s.courseId == null) continue;
            int[] counts = perCourse.get(s.courseId);
            if (counts == null) perCourse.put(s.courseId, counts = new int[3]);
            if (Attendance.attended(e.getValue().status)) counts[0]++;
            counts[1]++;
            if (Attendance.LATE.equals(e.getValue().status)) counts[2]++;
        }
        for (Map.Entry<String, int[]> e : perCourse.entrySet()) {
            int[] c = e.getValue();
            int pct = Formats.percent(c[0], c[1]);
            Course course = SeedCache.course(e.getKey());
            String code = course == null ? e.getKey()
                    : course.hasRealCode() ? course.code : course.name;
            String name = course != null && course.hasRealCode() ? course.name : "";

            ItemCourseCardBinding card = ItemCourseCardBinding.inflate(getLayoutInflater(),
                    binding.listCourses, false);
            card.courseCode.setText(code);
            card.courseName.setText(name);
            card.courseName.setVisibility(name.isEmpty() ? View.GONE : View.VISIBLE);
            card.coursePct.setText(getString(R.string.percent, pct));
            card.courseBar.setProgress(pct);
            card.courseDetail.setText(SessionText.join(
                    getResources().getQuantityString(R.plurals.n_of_total_sessions, c[1], c[0], c[1]),
                    c[2] > 0 ? getString(R.string.n_late, c[2]) : null));
            card.getRoot().setContentDescription(getString(R.string.course_pct_description,
                    code, pct));
            binding.listCourses.addView(card.getRoot());
        }
    }

    /** Every recorded session, newest first: "ICT602 · Mon, 12 Oct · 9:00 AM" + status. */
    private void renderHistory(Map<String, Mark> all) {
        binding.listHistory.removeAllViews();
        List<Session> list = new ArrayList<>();
        for (String sid : all.keySet()) {
            Session s = sessions.get(sid);
            if (s != null) list.add(s);
        }
        binding.groupHistory.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        Collections.sort(list, (a, b) -> Long.compare(b.start, a.start));
        for (Session s : list) {
            ItemSessionRowBinding row = ItemSessionRowBinding.inflate(getLayoutInflater(),
                    binding.listHistory, false);
            row.rowIcon.setImageResource(SessionText.icon(s));
            row.rowTitle.setText(s.title);
            row.rowMeta.setText(SessionText.join(SessionText.course(s),
                    Formats.shortDate(s.start), Formats.time(s.start)));
            AttendeeText.statusChip(row.rowChip, all.get(s.id).status);
            Ui.addRow(binding.listHistory, row.getRoot());
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
