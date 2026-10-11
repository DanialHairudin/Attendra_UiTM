package com.attendra.uitm.ui.lecturer;

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
import com.attendra.uitm.databinding.FragmentLecturerHomeBinding;
import com.attendra.uitm.databinding.ItemSessionRowBinding;
import com.attendra.uitm.model.Attendance;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.model.Session;
import com.attendra.uitm.ui.common.ComingSoonActivity;
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

/**
 * Lecturer Dashboard Page (CLAUDE.md 12.5).
 *
 * Data, all live (ValueEventListeners added in onStart, removed in onStop):
 * - lecturers/{uid}                → greeting and avatar
 * - sessionsByLecturer/{uid}       → the lecturer's sessions (via SessionWatcher)
 * - for the open session:
 *     attendance/{sid}             → "Checked in"
 *     studentsByGroup/{group} or clubMembers/{club} → "Expected"
 *     attempts/{sid}               → "Rejected"
 * Nothing is made up: with no sessions the cards show empty states.
 */
public class LecturerHomeFragment extends Fragment {

    /** Re-check times every 30 s, so "Scheduled" turns "Live" without new data. */
    private static final long TICK_MS = 30_000;
    /** Newest sessions to follow. Older ones belong to History (C11). */
    private static final int MAX_SESSIONS = 100;

    private FragmentLecturerHomeBinding binding;
    private final Subscriptions subs = new Subscriptions();
    private final Subscriptions liveSubs = new Subscriptions();
    private SessionWatcher watcher;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, TICK_MS);
        }
    };

    private Map<String, Session> sessions = new HashMap<>();
    private boolean sessionsReady;
    private boolean seedReady;

    // Live-session counts; -1 = not loaded yet (shown as "–")
    private String liveId;
    private final Set<String> checkedIn = new HashSet<>();
    private final Set<String> rejectedUids = new HashSet<>();
    private final Map<String, Integer> expectedByIndex = new HashMap<>();
    private boolean attendanceLoaded;
    private boolean attemptsLoaded;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentLecturerHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Ui.setEmpty(binding.emptyToday, R.drawable.ic_clock,
                R.string.empty_today_title, R.string.empty_today_body);
        binding.btnAvatar.setOnClickListener(v ->
                ProfileActivity.open(requireContext(), Registration.ROLE_LECTURER));
        binding.btnSeeHistory.setOnClickListener(v -> host().selectTab(R.id.nav_history));
        binding.btnCreateSession.setOnClickListener(v -> host().openCreateSession());
        binding.btnLiveMonitor.setOnClickListener(v -> ComingSoonActivity.open(requireContext(),
                R.string.open_live_monitor, R.string.live_monitor_later));
        binding.txtDate.setText(Formats.dateLine(now()));
    }

    @Override
    public void onStart() {
        super.onStart();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String uid = user.getUid();

        subs.listen(UserRepository.profileRef(uid, Registration.ROLE_LECTURER),
                new Callback<DataSnapshot>() {
                    @Override
                    public void onSuccess(DataSnapshot profile) {
                        String name = profile.child("name").getValue(String.class);
                        if (binding == null || name == null) return;
                        binding.txtGreeting.setText(Ui.greeting(requireContext(),
                                Names.greetingName(profile), now()));
                        binding.btnAvatar.setText(Names.initials(name));
                    }

                    @Override
                    public void onError(String message) {
                        showError(message);
                    }
                });

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
        watcher.watchIndex("lecturer", Db.root().child("sessionsByLecturer").child(uid)
                .limitToLast(MAX_SESSIONS));
        handler.post(tick);
    }

    @Override
    public void onStop() {
        super.onStop();
        subs.clear();
        if (watcher != null) watcher.stop();
        stopLive();
        handler.removeCallbacks(tick);
    }

    // ------------------------------------------------------------------ render

    private void render() {
        if (binding == null) return;
        long now = now();
        binding.txtDate.setText(Formats.dateLine(now));
        if (!seedReady) return; // course and room names are needed for every row
        renderLive(now);
        renderToday(now);
    }

    /** The open session that closes first, or null. */
    @Nullable
    private Session findLive() {
        Session live = null;
        for (Session s : sessions.values()) {
            if (s.isOpen() && (live == null || s.end < live.end)) live = s;
        }
        return live;
    }

    private void renderLive(long now) {
        Session live = findLive();
        if (live == null) {
            binding.cardLive.setVisibility(View.GONE);
            stopLive();
            return;
        }
        if (!live.id.equals(liveId)) startLive(live);

        binding.cardLive.setVisibility(View.VISIBLE);
        binding.txtLiveCloses.setText(getString(R.string.closes_at, Formats.time(live.end)));
        binding.txtLiveTitle.setText(live.title);
        binding.txtLiveCourse.setText(SessionText.join(SessionText.course(live),
                SessionText.groups(live)));
        binding.txtLiveRoom.setText(SessionText.room(requireContext(), live));

        binding.txtCountChecked.setText(attendanceLoaded
                ? String.valueOf(checkedIn.size()) : getString(R.string.dash));
        binding.txtCountExpected.setText(expectedByIndex.containsValue(-1)
                ? getString(R.string.dash) : String.valueOf(sum(expectedByIndex)));
        // Rejected = students with a rejected attempt who have not (yet) checked in.
        Set<String> rejected = new HashSet<>(rejectedUids);
        rejected.removeAll(checkedIn);
        binding.txtCountRejected.setText(attemptsLoaded && attendanceLoaded
                ? String.valueOf(rejected.size()) : getString(R.string.dash));
    }

    /** Listens to the counts for the open session. */
    private void startLive(Session live) {
        stopLive();
        liveId = live.id;

        liveSubs.listen(Db.root().child("attendance").child(live.id), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                checkedIn.clear();
                for (DataSnapshot rec : snapshot.getChildren()) {
                    if (Attendance.attended(rec.child("status").getValue(String.class))) {
                        checkedIn.add(rec.getKey());
                    }
                }
                attendanceLoaded = true;
                render();
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });

        liveSubs.listen(Db.root().child("attempts").child(live.id), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                rejectedUids.clear();
                for (DataSnapshot attempt : snapshot.getChildren()) {
                    String result = attempt.child("result").getValue(String.class);
                    String uid = attempt.child("uid").getValue(String.class);
                    if (Attendance.RESULT_REJECTED.equals(result) && uid != null) {
                        rejectedUids.add(uid);
                    }
                }
                attemptsLoaded = true;
                render();
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });

        // Expected = everyone who may attend: the members of the club, or the
        // students of every group in the session.
        List<String> indexes = new ArrayList<>();
        if (live.isClub()) {
            indexes.add("clubMembers/" + live.clubId);
        } else {
            for (String g : live.groupIds()) indexes.add("studentsByGroup/" + g);
        }
        for (String path : indexes) {
            expectedByIndex.put(path, -1);
            liveSubs.listen(Db.root().child(path), new Callback<DataSnapshot>() {
                @Override
                public void onSuccess(DataSnapshot snapshot) {
                    expectedByIndex.put(path, (int) snapshot.getChildrenCount());
                    render();
                }

                @Override
                public void onError(String message) {
                    showError(message);
                }
            });
        }
    }

    private void stopLive() {
        liveSubs.clear();
        liveId = null;
        checkedIn.clear();
        rejectedUids.clear();
        expectedByIndex.clear();
        attendanceLoaded = false;
        attemptsLoaded = false;
    }

    private void renderToday(long now) {
        List<Session> today = new ArrayList<>();
        for (Session s : sessions.values()) {
            if (Formats.sameDay(s.start, now)) today.add(s);
        }
        Collections.sort(today, (a, b) -> Long.compare(a.start, b.start));

        binding.listToday.removeAllViews();
        if (today.isEmpty()) {
            Ui.showListState(sessionsReady ? Ui.ListState.EMPTY : Ui.ListState.LOADING,
                    binding.txtTodayLoading, binding.emptyToday, binding.listToday);
            return;
        }
        Ui.showListState(Ui.ListState.CONTENT, binding.txtTodayLoading, binding.emptyToday,
                binding.listToday);
        for (Session s : today) {
            ItemSessionRowBinding row = ItemSessionRowBinding.inflate(getLayoutInflater(),
                    binding.listToday, false);
            row.rowIcon.setImageResource(SessionText.icon(s));
            row.rowTitle.setText(s.title);
            row.rowMeta.setText(SessionText.join(
                    s.isClub() ? SessionText.course(s) : SessionText.groups(s),
                    SessionText.room(requireContext(), s),
                    Formats.timeRange(s.start, s.end)));
            SessionText.statusChip(row.rowChip, s, now);
            row.getRoot().setOnClickListener(v -> {
                if (s.isOpen()) {
                    ComingSoonActivity.open(requireContext(), R.string.open_live_monitor,
                            R.string.live_monitor_later);
                } else {
                    ComingSoonActivity.open(requireContext(), R.string.session_report,
                            R.string.session_report_later);
                }
            });
            Ui.addRow(binding.listToday, row.getRoot());
        }
    }

    // ----------------------------------------------------------------- helpers

    private static int sum(Map<String, Integer> counts) {
        int total = 0;
        for (int n : counts.values()) total += n;
        return total;
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private void showError(String message) {
        if (binding != null) Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }

    private LecturerMainActivity host() {
        return (LecturerMainActivity) requireActivity();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
