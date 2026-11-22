package com.attendra.uitm.ui.student;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.fragment.app.Fragment;

import com.attendra.uitm.R;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.Db;
import com.attendra.uitm.data.HiddenSessions;
import com.attendra.uitm.data.SeedCache;
import com.attendra.uitm.data.Subscriptions;
import com.attendra.uitm.databinding.MyAttendancePageBinding;
import com.attendra.uitm.databinding.ItemCourseCardBinding;
import com.attendra.uitm.databinding.ItemSessionRowBinding;
import com.attendra.uitm.model.Attendance;
import com.attendra.uitm.model.Course;
import com.attendra.uitm.model.Session;
import com.attendra.uitm.ui.common.AttendeeText;
import com.attendra.uitm.ui.common.SessionText;
import com.attendra.uitm.ui.common.Ui;
import com.attendra.uitm.util.Formats;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** My Attendance Page. */
public class MyAttendancePage extends Fragment {

    /** One studentAttendance entry. */
    private static final class Mark {
        final String status;
        final long time;

        Mark(String status, long time) {
            this.status = status;
            this.time = time;
        }
    }

    private MyAttendancePageBinding binding;
    private final Subscriptions subs = new Subscriptions();
    /** sessionId → my mark. */
    @Nullable
    private Map<String, Mark> marks;
    /** sessionId → session (null value = the session was deleted). */
    private final Map<String, Session> sessions = new HashMap<>();
    private final Set<String> lookups = new HashSet<>();
    private boolean seedReady;
    /** hiddenSessions/{uid}: left out of the history, still counted in every percentage. */
    private Set<String> hidden = new HashSet<>();
    /** "Show hidden sessions" switch at the bottom. */
    private boolean showHidden;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = MyAttendancePageBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Ui.setEmpty(binding.empty, R.drawable.ic_chart,
                R.string.empty_courses_title, R.string.empty_courses_body);
        binding.rowShowHidden.switchTitle.setText(R.string.show_hidden);
        binding.rowShowHidden.switchToggle.setContentDescription(getString(R.string.show_hidden));
        binding.rowShowHidden.switchToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                showHidden = on;
                render();
            }
        });
        binding.rowShowHidden.getRoot().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                binding.rowShowHidden.switchToggle.toggle();
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        loadSeed();

        watchMarks(user);

        watchHidden(user);
    }

    /** Loads course names. */
    private void loadSeed() {
        SeedCache.load(new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                seedReady = true;
                render();
            }

            @Override
            public void onError(String message) {
                seedReady = true; // Course names fall back to their IDs
                showError(message);
                render();
            }
        });
    }

    /** Follows the student's attendance records. */
    private void watchMarks(FirebaseUser user) {
        subs.listen(Db.root().child("studentAttendance").child(user.getUid()),
                new Callback<DataSnapshot>() {
                    @Override
                    public void onSuccess(DataSnapshot snapshot) {
                        Map<String, Mark> map = new LinkedHashMap<>();
                        for (DataSnapshot rec : snapshot.getChildren()) {
                            String status = rec.child("status").getValue(String.class);
                            Long time = rec.child("time").getValue(Long.class);
                            if (status != null) {
                                if (time == null) time = 0L;
                                map.put(rec.getKey(), new Mark(status, time));
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

    /** Follows the sessions the student has hidden. */
    private void watchHidden(FirebaseUser user) {
        subs.listen(HiddenSessions.ref(user.getUid()), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                hidden = HiddenSessions.ids(snapshot);
                render();
            }

            @Override
            public void onError(String message) {
                showError(message);
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
                if (s != null) {
                    s.id = sid;
                }
                sessions.put(sid, s);
                render();
            }

            @Override
            public void onError(String message) {
                lookups.remove(sid); // Try again on the next update
            }
        });
    }

    // ------------------------------------------------------------------ render

    /** Draws the whole page again. */
    private void render() {
        if (binding == null || !seedReady) return;
        Map<String, Mark> all = marks;
        boolean loading = all == null || !sessions.keySet().containsAll(all.keySet());
        if (loading) {
            binding.txtLoading.setVisibility(View.VISIBLE);
        } else {
            binding.txtLoading.setVisibility(View.GONE);
        }
        if (all == null) return;

        renderOverall(all);
        boolean empty = all.isEmpty();
        if (empty) {
            binding.cardEmpty.setVisibility(View.VISIBLE);
        } else {
            binding.cardEmpty.setVisibility(View.GONE);
        }
        renderCourses(all);
        renderHistory(all);
    }

    /** "92%" and the present / late / absent chips, out of every recorded session. */
    private void renderOverall(Map<String, Mark> all) {
        int present = 0;
        int late = 0;
        for (Mark m : all.values()) {
            if (Attendance.PRESENT.equals(m.status)) {
                present++;
            } else if (Attendance.LATE.equals(m.status)) {
                late++;
            }
        }
        int total = all.size();
        int absent = total - present - late;
        if (total == 0) {
            binding.txtOverallPct.setText(getString(R.string.dash));
        } else {
            binding.txtOverallPct.setText(getString(R.string.percent, Formats.percent(present + late,
                    total)));
        }
        binding.chipPresent.setText(getString(R.string.n_present, present));
        binding.chipLate.setText(getString(R.string.n_late, late));
        binding.chipAbsent.setText(getString(R.string.n_absent, absent));
        binding.txtOfTotal.setText(getString(R.string.of_total, total));
    }

    /** One card per course: {attended, total, late}. */
    private void renderCourses(Map<String, Mark> all) {
        binding.listCourses.removeAllViews();
        Map<String, int[]> perCourse = new TreeMap<>();
        for (Map.Entry<String, Mark> e : all.entrySet()) {
            Session s = sessions.get(e.getKey());
            if (s == null || s.isClub() || s.courseId == null) continue;
            int[] counts = perCourse.get(s.courseId);
            if (counts == null) {
                counts = new int[3];
                perCourse.put(s.courseId, counts);
            }
            if (Attendance.attended(e.getValue().status)) {
                counts[0]++;
            }
            counts[1]++;
            if (Attendance.LATE.equals(e.getValue().status)) {
                counts[2]++;
            }
        }
        for (Map.Entry<String, int[]> e : perCourse.entrySet()) {
            addCourseCard(e);
        }
    }

    /** Adds the card of one course. */
    private void addCourseCard(Map.Entry<String, int[]> e) {
        int[] c = e.getValue();
        int pct = Attendance.percent(c[0], c[1]);
        Course course = SeedCache.course(e.getKey());
        String code;
        if (course == null) {
            code = e.getKey();
        } else if (course.hasRealCode()) {
            code = course.code;
        } else {
            code = course.name;
        }
        String name;
        if (course != null && course.hasRealCode()) {
            name = course.name;
        } else {
            name = "";
        }

        ItemCourseCardBinding card = ItemCourseCardBinding.inflate(getLayoutInflater(),
                binding.listCourses, false);
        card.courseCode.setText(code);
        card.courseName.setText(name);
        if (name.isEmpty()) {
            card.courseName.setVisibility(View.GONE);
        } else {
            card.courseName.setVisibility(View.VISIBLE);
        }
        card.coursePct.setText(getString(R.string.percent, pct));
        card.courseBar.setProgress(pct);
        if (c[2] > 0) {
            card.courseDetail.setText(SessionText.join(getResources().getQuantityString(
                    R.plurals.n_of_total_sessions, c[1], c[0], c[1]), getString(R.string.n_late,
                            c[2])));
        } else {
            card.courseDetail.setText(SessionText.join(getResources().getQuantityString(
                    R.plurals.n_of_total_sessions, c[1], c[0], c[1]), null));
        }
        String warning = null;
        if (Attendance.belowLimit(c[0], c[1])) {
            warning = getString(R.string.below_limit_student, Attendance.ATTENDANCE_LIMIT);
            card.courseWarning.setText(warning);
            card.courseWarning.setVisibility(View.VISIBLE);
        } else {
            card.courseWarning.setVisibility(View.GONE);
        }
        card.getRoot().setContentDescription(SessionText.join(
                getString(R.string.course_pct_description, code, pct), warning));
        binding.listCourses.addView(card.getRoot());
    }

    /** Every recorded session, newest first: "ICT602 · Mon, 12 Oct · 9:00 AM" + status. */
    private void renderHistory(Map<String, Mark> all) {
        binding.listHistory.removeAllViews();
        List<Session> list = new ArrayList<>();
        int hiddenCount = 0;
        for (String sid : all.keySet()) {
            Session s = sessions.get(sid);
            if (s == null) continue;
            if (hidden.contains(sid)) {
                hiddenCount++;
                if (!showHidden) continue;
            }
            list.add(s);
        }
        if (list.isEmpty()) {
            binding.groupHistory.setVisibility(View.GONE);
        } else {
            binding.groupHistory.setVisibility(View.VISIBLE);
        }
        if (hiddenCount > 0) {
            binding.cardShowHidden.setVisibility(View.VISIBLE);
        } else {
            binding.cardShowHidden.setVisibility(View.GONE);
        }
        binding.rowShowHidden.switchSubtitle.setText(getResources().getQuantityString(
                R.plurals.hidden_count, hiddenCount, hiddenCount));
        Collections.sort(list, new Comparator<Session>() {
            @Override
            public int compare(Session a, Session b) {
                return Long.compare(b.start, a.start);
            }
        });
        for (Session s : list) {
            addHistoryRow(s, all);
        }
    }

    /** Adds the row of one session. */
    private void addHistoryRow(Session s, Map<String, Mark> all) {
        boolean isHidden = hidden.contains(s.id);
        ItemSessionRowBinding row = ItemSessionRowBinding.inflate(getLayoutInflater(),
                binding.listHistory, false);
        row.rowIcon.setImageResource(SessionText.icon(s));
        row.rowTitle.setText(s.title);
        if (isHidden) {
            row.rowMeta.setText(SessionText.join(SessionText.course(s),
                    Formats.shortDate(s.start), Formats.time(s.start),
                            getString(R.string.hidden_label)));
        } else {
            row.rowMeta.setText(SessionText.join(SessionText.course(s),
                    Formats.shortDate(s.start), Formats.time(s.start), null));
        }
        AttendeeText.statusChip(row.rowChip, all.get(s.id).status);
        if (isHidden) {
            row.getRoot().setAlpha(0.6f);
        } else {
            row.getRoot().setAlpha(1f);
        }
        row.getRoot().setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (isHidden) {
                    confirmUnhide(s);
                } else {
                    confirmHide(s);
                }
                return true;
            }
        });
        if (isHidden) {
            ViewCompat.replaceAccessibilityAction(row.getRoot(),
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK,
                            getString(R.string.unhide_session), null);
        } else {
            ViewCompat.replaceAccessibilityAction(row.getRoot(),
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK,
                            getString(R.string.hide_session), null);
        }
        Ui.addRow(binding.listHistory, row.getRoot());
    }

    // --------------------------------------------------------- hide

    /** Only closed sessions can be hidden (the rules check this too). */
    private void confirmHide(Session s) {
        if (s.isOpen()) {
            showError(getString(R.string.hide_open_refused));
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.hide_session_title)
                .setMessage(R.string.hide_session_body)
                .setPositiveButton(R.string.hide_session, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String uid = FirebaseAuth.getInstance().getUid();
                        if (uid != null) {
                            HiddenSessions.hide(uid, s.id, snackOnError());
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Asks before showing a hidden session again. */
    private void confirmUnhide(Session s) {
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.unhide_session_body)
                .setPositiveButton(R.string.unhide_session, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String uid = FirebaseAuth.getInstance().getUid();
                        if (uid != null) {
                            HiddenSessions.unhide(uid, s.id, snackOnError());
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** The hiddenSessions listener redraws the list; only errors need handling. */
    private Callback<Void> snackOnError() {
        return new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        };
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
