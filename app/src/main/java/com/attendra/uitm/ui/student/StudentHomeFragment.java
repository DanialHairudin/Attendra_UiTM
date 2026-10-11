package com.attendra.uitm.ui.student;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.attendra.uitm.data.SessionWatcher;
import com.attendra.uitm.data.Subscriptions;
import com.attendra.uitm.data.UserRepository;
import com.attendra.uitm.databinding.FragmentStudentHomeBinding;
import com.attendra.uitm.databinding.ItemCourseRowBinding;
import com.attendra.uitm.model.Attendance;
import com.attendra.uitm.model.Course;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.model.Session;
import com.attendra.uitm.ui.common.ComingSoonActivity;
import com.attendra.uitm.ui.common.MainTabsActivity;
import com.attendra.uitm.ui.common.SessionText;
import com.attendra.uitm.ui.common.Ui;
import com.attendra.uitm.ui.settings.ProfileActivity;
import com.attendra.uitm.util.Formats;
import com.attendra.uitm.util.Names;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Student Dashboard Page (CLAUDE.md 12.5).
 *
 * Data, all live (ValueEventListeners added in onStart, removed in onStop):
 * - students/{uid}                 → greeting, avatar and the student's group
 * - studentClubs/{uid}             → the clubs the student joined
 * - sessionsByGroup/{groupId} and sessionsByClub/{clubId} → sessions for me
 *   (SessionWatcher), used for the open-session card
 * - studentAttendance/{uid}        → overall percentage and "My courses"
 * Course names for "My courses" need each attended session's courseId, which is
 * read once per session (sessions/{sid}) because a session's course never changes.
 */
public class StudentHomeFragment extends Fragment {

    private static final long TICK_MS = 30_000;
    private static final String INDEX_GROUP = "group";
    private static final String INDEX_CLUB = "club:";

    private FragmentStudentHomeBinding binding;
    private final Subscriptions subs = new Subscriptions();
    private SessionWatcher watcher;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, TICK_MS);
        }
    };

    private boolean seedReady;
    private Map<String, Session> mySessions = new HashMap<>();
    private String watchedGroup;
    /** sessionId → status ("present", "late", "absent"); null = not loaded yet. */
    private Map<String, String> myAttendance;
    /** sessionId → courseId ("" for club sessions), read once per session. */
    private final Map<String, String> courseOfSession = new HashMap<>();
    private final Set<String> courseLookups = new HashSet<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentStudentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Ui.setEmpty(binding.emptyCourses, R.drawable.ic_chart,
                R.string.empty_courses_title, R.string.empty_courses_body);
        binding.txtDate.setText(Formats.dateLine(System.currentTimeMillis()));
        binding.btnAvatar.setOnClickListener(v ->
                ProfileActivity.open(requireContext(), Registration.ROLE_STUDENT));
        binding.btnSeeAll.setOnClickListener(v ->
                ((MainTabsActivity) requireActivity()).selectTab(R.id.nav_attendance));
        binding.btnCheckIn.setOnClickListener(v -> ComingSoonActivity.open(requireContext(),
                R.string.check_in, R.string.check_in_later));
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

        watcher = new SessionWatcher(new SessionWatcher.Listener() {
            @Override
            public void onSessions(Map<String, Session> all, boolean ready) {
                mySessions = all;
                render();
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });

        subs.listen(UserRepository.profileRef(uid, Registration.ROLE_STUDENT),
                new Callback<DataSnapshot>() {
                    @Override
                    public void onSuccess(DataSnapshot profile) {
                        String name = profile.child("name").getValue(String.class);
                        if (binding != null && name != null) {
                            binding.txtGreeting.setText(getString(R.string.greeting_student,
                                    Names.firstName(name)));
                            binding.btnAvatar.setText(Names.initials(name));
                        }
                        // Follow the sessions of the student's (current) group.
                        String groupId = profile.child("groupId").getValue(String.class);
                        if (groupId != null && !groupId.equals(watchedGroup)) {
                            watcher.unwatchIndex(INDEX_GROUP);
                            watcher.watchIndex(INDEX_GROUP,
                                    Db.root().child("sessionsByGroup").child(groupId));
                            watchedGroup = groupId;
                        }
                    }

                    @Override
                    public void onError(String message) {
                        showError(message);
                    }
                });

        subs.listen(UserRepository.clubsQuery(uid, Registration.ROLE_STUDENT),
                new Callback<DataSnapshot>() {
                    @Override
                    public void onSuccess(DataSnapshot snapshot) {
                        List<String> clubs = UserRepository.clubIds(snapshot, uid,
                                Registration.ROLE_STUDENT);
                        // Follow sessionsByClub for every joined club, and stop
                        // following clubs the student has left.
                        for (String name : watcher.indexNames()) {
                            if (name.startsWith(INDEX_CLUB)
                                    && !clubs.contains(name.substring(INDEX_CLUB.length()))) {
                                watcher.unwatchIndex(name);
                            }
                        }
                        for (String clubId : clubs) {
                            watcher.watchIndex(INDEX_CLUB + clubId,
                                    Db.root().child("sessionsByClub").child(clubId));
                        }
                    }

                    @Override
                    public void onError(String message) {
                        showError(message);
                    }
                });

        subs.listen(Db.root().child("studentAttendance").child(uid), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                Map<String, String> map = new HashMap<>();
                for (DataSnapshot rec : snapshot.getChildren()) {
                    String status = rec.child("status").getValue(String.class);
                    if (status != null) map.put(rec.getKey(), status);
                }
                myAttendance = map;
                lookUpCourses(map.keySet());
                render();
            }

            @Override
            public void onError(String message) {
                myAttendance = new HashMap<>();
                showError(message);
                render();
            }
        });

        handler.post(tick);
    }

    @Override
    public void onStop() {
        super.onStop();
        subs.clear();
        if (watcher != null) watcher.stop();
        handler.removeCallbacks(tick);
        myAttendance = null;
        watchedGroup = null;
    }

    /** Reads sessions/{sid}/courseId once for each attended session we don't know yet. */
    private void lookUpCourses(Set<String> sessionIds) {
        for (String sid : sessionIds) {
            if (courseOfSession.containsKey(sid) || !courseLookups.add(sid)) continue;
            Db.readOnce(Db.root().child("sessions").child(sid).child("courseId"),
                    new Callback<DataSnapshot>() {
                        @Override
                        public void onSuccess(DataSnapshot snapshot) {
                            String courseId = snapshot.getValue(String.class);
                            courseOfSession.put(sid, courseId == null ? "" : courseId);
                            render();
                        }

                        @Override
                        public void onError(String message) {
                            courseLookups.remove(sid); // try again on the next update
                        }
                    });
        }
    }

    // ------------------------------------------------------------------ render

    private void render() {
        if (binding == null || !seedReady) return;
        long now = System.currentTimeMillis();
        binding.txtDate.setText(Formats.dateLine(now));
        renderOpenSession(now);
        renderOverall();
        renderCourses();
    }

    /** The card shows the open session (for my group or clubs) that closes first. */
    private void renderOpenSession(long now) {
        Session open = null;
        for (Session s : mySessions.values()) {
            if (s.isCheckInOpen(now) && (open == null || s.end < open.end)) open = s;
        }
        if (open == null) {
            binding.cardOpen.setVisibility(View.GONE);
            return;
        }
        binding.cardOpen.setVisibility(View.VISIBLE);
        binding.txtOpenCloses.setText(getString(R.string.closes_at, Formats.time(open.end)));
        binding.txtOpenTitle.setText(open.title);
        binding.txtOpenMeta.setText(SessionText.join(SessionText.course(open),
                SessionText.room(requireContext(), open)));

        boolean done = myAttendance != null && Attendance.attended(myAttendance.get(open.id));
        binding.btnCheckIn.setVisibility(done ? View.GONE : View.VISIBLE);
        binding.txtCheckedIn.setVisibility(done ? View.VISIBLE : View.GONE);
    }

    private void renderOverall() {
        if (myAttendance == null) {
            binding.txtOverallPct.setText(R.string.dash);
            binding.txtOverallDetail.setText(R.string.loading);
            return;
        }
        int total = myAttendance.size();
        if (total == 0) {
            binding.txtOverallPct.setText(R.string.dash);
            binding.txtOverallDetail.setText(R.string.no_attendance_yet);
            return;
        }
        int attended = 0;
        for (String status : myAttendance.values()) {
            if (Attendance.attended(status)) attended++;
        }
        binding.txtOverallPct.setText(getString(R.string.percent,
                Formats.percent(attended, total)));
        binding.txtOverallDetail.setText(getString(R.string.overall_detail, attended, total));
    }

    /** One row per course: attended / recorded sessions of that course. Club sessions are left out. */
    private void renderCourses() {
        binding.listCourses.removeAllViews();
        if (myAttendance == null) {
            Ui.showListState(Ui.ListState.LOADING, binding.txtCoursesLoading,
                    binding.emptyCourses, binding.listCourses);
            return;
        }
        // courseId → {attended, total}; TreeMap keeps a stable order
        Map<String, int[]> perCourse = new TreeMap<>();
        boolean waiting = false;
        for (Map.Entry<String, String> e : myAttendance.entrySet()) {
            String courseId = courseOfSession.get(e.getKey());
            if (courseId == null) {
                waiting = true; // still reading this session's course
                continue;
            }
            if (courseId.isEmpty()) continue; // club session
            int[] counts = perCourse.get(courseId);
            if (counts == null) perCourse.put(courseId, counts = new int[2]);
            if (Attendance.attended(e.getValue())) counts[0]++;
            counts[1]++;
        }
        if (perCourse.isEmpty()) {
            Ui.showListState(waiting ? Ui.ListState.LOADING : Ui.ListState.EMPTY,
                    binding.txtCoursesLoading, binding.emptyCourses, binding.listCourses);
            return;
        }
        Ui.showListState(Ui.ListState.CONTENT, binding.txtCoursesLoading, binding.emptyCourses,
                binding.listCourses);

        List<String> ids = new ArrayList<>(perCourse.keySet());
        Collections.sort(ids, (a, b) -> label(a).compareToIgnoreCase(label(b)));
        for (String courseId : ids) {
            int[] counts = perCourse.get(courseId);
            int pct = Formats.percent(counts[0], counts[1]);
            ItemCourseRowBinding row = ItemCourseRowBinding.inflate(getLayoutInflater(),
                    binding.listCourses, false);
            row.courseTitle.setText(label(courseId));
            row.courseBar.setProgress(pct);
            row.coursePct.setText(getString(R.string.percent, pct));
            row.getRoot().setContentDescription(getString(R.string.course_pct_description,
                    label(courseId), pct));
            row.getRoot().setOnClickListener(v ->
                    ((MainTabsActivity) requireActivity()).selectTab(R.id.nav_attendance));
            Ui.addRow(binding.listCourses, row.getRoot());
        }
    }

    private String label(String courseId) {
        Course c = SeedCache.course(courseId);
        return c != null ? c.label() : courseId;
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
