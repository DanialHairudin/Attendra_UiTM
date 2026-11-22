package com.attendra.uitm.ui.lecturer;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.attendra.uitm.R;
import com.attendra.uitm.data.AttendanceJava;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.Db;
import com.attendra.uitm.data.SeedCache;
import com.attendra.uitm.data.SessionWatcher;
import com.attendra.uitm.data.Subscriptions;
import com.attendra.uitm.data.TestWorld;
import com.attendra.uitm.data.UserJava;
import com.attendra.uitm.databinding.LecturerHomePageBinding;
import com.attendra.uitm.databinding.ItemSessionRowBinding;
import com.attendra.uitm.model.Attendance;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.model.Session;

import com.attendra.uitm.ui.common.SessionText;
import com.attendra.uitm.ui.common.Ui;
import com.attendra.uitm.ui.settings.ProfilePage;
import com.attendra.uitm.util.Formats;
import com.attendra.uitm.util.Names;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lecturer Dashboard Page. */
public class LecturerHomePage extends Fragment {

    /** Re-check times every 30 s, so "Scheduled" turns "Live" without new data. */
    private static final long TICK_MS = 30_000;
    /** Newest sessions to follow. */
    private static final int MAX_SESSIONS = 400; // About 2 semesters of weekly sessions

    private LecturerHomePageBinding binding;
    private final Subscriptions subs = new Subscriptions();
    private final Subscriptions liveSubs = new Subscriptions();
    private final AttendanceJava attendance = new AttendanceJava();
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
        binding = LecturerHomePageBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Ui.setEmpty(binding.emptyToday, R.drawable.ic_clock,
                R.string.empty_today_title, R.string.empty_today_body);
        binding.btnAvatar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ProfilePage.open(requireContext(), Registration.ROLE_LECTURER);
            }
        });
        binding.btnSeeHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host().selectTab(R.id.nav_history);
            }
        });
        binding.btnCreateSession.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host().openCreateSession();
            }
        });
        binding.btnLiveMonitor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (liveId != null) {
                    LiveMonitorPage.open(requireContext(), liveId);
                }
            }
        });
        binding.txtDate.setText(Formats.dateLine(now()));
    }

    @Override
    public void onStart() {
        super.onStart();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String uid = user.getUid();

        subs.listen(UserJava.profileRef(uid, Registration.ROLE_LECTURER),
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
        if (watcher != null) {
            watcher.stop();
        }
        stopLive();
        handler.removeCallbacks(tick);
    }

    // ------------------------------------------------------------------ render

    /** Draws the whole page again. */
    private void render() {
        if (binding == null) return;
        long now = now();
        binding.txtDate.setText(Formats.dateLine(now));
        autoCloseOverdue(now);
        if (!seedReady) return; // Course and room names are needed for every row
        renderLive(now);
        renderToday(now);
    }

    /**
     * Auto-close: a session still "open" after its end time is closed the same way as the Close
     * button, so no-shows get Absent.
     */
    private void autoCloseOverdue(long now) {
        if (!sessionsReady) return; // Wait for every session, so all past ones close together
        attendance.closeOverdue(sessions.values(), now, new Callback<Integer>() {
            @Override
            public void onSuccess(Integer closed) {
                if (binding == null) return;
                Toast.makeText(requireContext(), getResources().getQuantityString(
                        R.plurals.past_sessions_closed, closed, closed), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                showError(message); // Tried again on the next tick
            }
        });
    }

    /** The open session that has started and closes first, or null. */
    @Nullable
    private Session findLive(long now) {
        Session live = null;
        for (Session s : sessions.values()) {
            if (s.isOpen() && now >= s.start && (live == null || s.end < live.end)) {
                live = s;
            }
        }
        return live;
    }

    /** The card of the live session. */
    private void renderLive(long now) {
        Session live = findLive(now);
        if (live == null) {
            binding.cardLive.setVisibility(View.GONE);
            stopLive();
            return;
        }
        if (!live.id.equals(liveId)) {
            startLive(live);
        }

        binding.cardLive.setVisibility(View.VISIBLE);
        binding.txtLiveCloses.setText(getString(R.string.closes_at, Formats.time(live.end)));
        binding.txtLiveTitle.setText(live.title);
        binding.txtLiveCourse.setText(SessionText.join(SessionText.course(live),
                SessionText.groups(live)));
        binding.txtLiveRoom.setText(SessionText.room(requireContext(), live));

        if (attendanceLoaded) {
            binding.txtCountChecked.setText(String.valueOf(checkedIn.size()));
        } else {
            binding.txtCountChecked.setText(getString(R.string.dash));
        }
        if (expectedByIndex.containsValue(-1)) {
            binding.txtCountExpected.setText(getString(R.string.dash));
        } else {
            binding.txtCountExpected.setText(String.valueOf(sum(expectedByIndex)));
        }
        // Rejected = students with a rejected attempt who have not (yet) checked in.
        Set<String> rejected = new HashSet<>(rejectedUids);
        rejected.removeAll(checkedIn);
        if (attemptsLoaded && attendanceLoaded) {
            binding.txtCountRejected.setText(String.valueOf(rejected.size()));
        } else {
            binding.txtCountRejected.setText(getString(R.string.dash));
        }
    }

    /** Listens to the counts for the open session. */
    private void startLive(Session live) {
        stopLive();
        liveId = live.id;

        watchCheckedIn(live);

        watchRejected(live);

        watchExpected(live);
    }

    /** Counts the students who checked in. */
    private void watchCheckedIn(Session live) {
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
    }

    /** Collects the students with a rejected attempt. */
    private void watchRejected(Session live) {
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
    }

    /** Counts everyone who may attend: club members, or the students of every group. */
    private void watchExpected(Session live) {
        // Expected = everyone who may attend: the members of the club, or the students of every
        // group in the session.
        List<String> indexes = new ArrayList<>();
        if (live.isClub()) {
            indexes.add("clubMembers/" + live.clubId);
        } else {
            for (String g : live.groupIds()) {
                indexes.add("studentsByGroup/" + g);
            }
        }
        for (String path : indexes) {
            expectedByIndex.put(path, -1);
            liveSubs.listen(Db.root().child(path), new Callback<DataSnapshot>() {
                @Override
                public void onSuccess(DataSnapshot snapshot) {
                    // Only students in the session's world (real or test) are expected.
                    TestWorld.filter(TestWorld.keys(snapshot), TestWorld.of(live),
                            new Callback<Set<String>>() {
                                @Override
                                public void onSuccess(Set<String> uids) {
                                    if (!expectedByIndex.containsKey(path)) return; // stopped
                                    expectedByIndex.put(path, uids.size());
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
            });
        }
    }

    /** Stops following the live session. */
    private void stopLive() {
        liveSubs.clear();
        liveId = null;
        checkedIn.clear();
        rejectedUids.clear();
        expectedByIndex.clear();
        attendanceLoaded = false;
        attemptsLoaded = false;
    }

    /** The sessions of today. */
    private void renderToday(long now) {
        List<Session> today = new ArrayList<>();
        for (Session s : sessions.values()) {
            if (Formats.sameDay(s.start, now)) {
                today.add(s);
            }
        }
        Collections.sort(today, new Comparator<Session>() {
            @Override
            public int compare(Session a, Session b) {
                return Long.compare(a.start, b.start);
            }
        });

        binding.listToday.removeAllViews();
        if (today.isEmpty()) {
            if (sessionsReady) {
                Ui.showListState(Ui.ListState.EMPTY, binding.txtTodayLoading, binding.emptyToday,
                        binding.listToday);
            } else {
                Ui.showListState(Ui.ListState.LOADING, binding.txtTodayLoading, binding.emptyToday,
                        binding.listToday);
            }
            return;
        }
        Ui.showListState(Ui.ListState.CONTENT, binding.txtTodayLoading, binding.emptyToday,
                binding.listToday);
        for (Session s : today) {
            ItemSessionRowBinding row = ItemSessionRowBinding.inflate(getLayoutInflater(),
                    binding.listToday, false);
            row.rowIcon.setImageResource(SessionText.icon(s));
            row.rowTitle.setText(s.title);
            if (s.isClub()) {
                row.rowMeta.setText(SessionText.join(SessionText.course(s),
                        SessionText.room(requireContext(), s), Formats.timeRange(s.start, s.end)));
            } else {
                row.rowMeta.setText(SessionText.join(SessionText.groups(s),
                        SessionText.room(requireContext(), s), Formats.timeRange(s.start, s.end)));
            }
            SessionText.statusChip(row.rowChip, s, now);
            row.getRoot().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (s.isOpen()) {
                        LiveMonitorPage.open(requireContext(), s.id);
                    } else {
                        SessionReportPage.open(requireContext(), s.id);
                    }
                }
            });
            Ui.addRow(binding.listToday, row.getRoot());
        }
    }

    // ----------------------------------------------------------------- helpers

    /** Adds up the numbers. */
    private static int sum(Map<String, Integer> counts) {
        int total = 0;
        for (int n : counts.values()) {
            total += n;
        }
        return total;
    }

    /** The time now in milliseconds. */
    private static long now() {
        return System.currentTimeMillis();
    }

    /** Shows an error message. */
    private void showError(String message) {
        if (binding != null) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
        }
    }

    /** The Activity this page is in. */
    private LecturerMainPage host() {
        return (LecturerMainPage) requireActivity();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
