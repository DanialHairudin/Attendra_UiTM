package com.attendra.uitm.ui.lecturer;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.fragment.app.Fragment;

import com.attendra.uitm.R;
import com.attendra.uitm.data.AttendanceJava;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.Db;
import com.attendra.uitm.data.SeedCache;
import com.attendra.uitm.data.SessionJava;
import com.attendra.uitm.data.SessionWatcher;
import com.attendra.uitm.databinding.HistoryPageBinding;
import com.attendra.uitm.databinding.ItemHistoryRowBinding;
import com.attendra.uitm.model.Attendance;
import com.attendra.uitm.model.Session;
import com.attendra.uitm.ui.common.SessionText;
import com.attendra.uitm.ui.common.Ui;
import com.attendra.uitm.util.Formats;
import com.google.android.material.chip.Chip;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lecturer History tab. */
public class HistoryPage extends Fragment {

    private static final int MAX_SESSIONS = 400; // About 2 semesters of weekly sessions
    private static final String F_ALL = "all";
    private static final String F_CLASS = "class";
    private static final String F_TEST = "test";
    private static final String F_ACTIVITY = "activity";
    private static final String F_WEEK = "week";
    private static final String F_UPCOMING = "upcoming";

    private HistoryPageBinding binding;
    private SessionWatcher watcher;
    private Map<String, Session> sessions = new HashMap<>();
    private boolean sessionsReady;
    private boolean seedReady;
    private String filter = F_ALL;
    /** sessionId → {attended, total} for closed sessions. */
    private final Map<String, int[]> counts = new HashMap<>();
    private final Set<String> lookups = new HashSet<>();

    /** Session Report, opened for a result so a delete made there shows "Session deleted" here. */
    private final ActivityResultLauncher<Intent> openReport = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == SessionReportPage.RESULT_DELETED) {
                        showDeleted();
                    }
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = HistoryPageBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        addFilter(F_ALL, R.string.filter_all);
        addFilter(F_CLASS, R.string.filter_class);
        addFilter(F_TEST, R.string.filter_test);
        addFilter(F_ACTIVITY, R.string.filter_activity);
        addFilter(F_WEEK, R.string.filter_this_week);
        addFilter(F_UPCOMING, R.string.filter_upcoming);
    }

    /** Adds one filter chip. */
    private void addFilter(String value, @StringRes int label) {
        Chip chip = new Chip(requireContext()); // Widget.Attendra.Pill from the theme
        chip.setText(label);
        chip.setCheckable(true);
        chip.setSaveEnabled(false);
        chip.setChecked(value.equals(filter));
        chip.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                if (checked) {
                    filter = value;
                    render();
                }
            }
        });
        binding.chipsFilter.addView(chip);
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
                seedReady = true;
                showError(message);
                render();
            }
        });
        watcher = new SessionWatcher(new SessionWatcher.Listener() {
            @Override
            public void onSessions(Map<String, Session> all, boolean ready) {
                sessions = all;
                sessionsReady = ready;
                render();
                closeOverdue();
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });
        watcher.watchIndex("lecturer", Db.root().child("sessionsByLecturer")
                .child(user.getUid()).limitToLast(MAX_SESSIONS));
    }

    @Override
    public void onStop() {
        super.onStop();
        if (watcher != null) {
            watcher.stop();
        }
    }

    /** Closes sessions that are past their end time, the same way as Home does. */
    private void closeOverdue() {
        if (!sessionsReady) return; // Wait for every session, so all past ones close together
        new AttendanceJava().closeOverdue(sessions.values(), System.currentTimeMillis(),
                new Callback<Integer>() {
                    @Override
                    public void onSuccess(Integer closed) {
                        if (binding == null) return;
                        Toast.makeText(requireContext(), getResources().getQuantityString(
                                R.plurals.past_sessions_closed, closed, closed),
                                Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(String message) {
                        showError(message);
                    }
                });
    }

    /** Reads attendance/{sid} once for a closed session we have no counts for. */
    private void lookUpCounts(Session s) {
        if (counts.containsKey(s.id) || !lookups.add(s.id)) return;
        Db.readOnce(Db.root().child("attendance").child(s.id), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                int attended = 0;
                int total = 0;
                for (DataSnapshot rec : snapshot.getChildren()) {
                    total++;
                    if (Attendance.attended(rec.child("status").getValue(String.class))) {
                        attended++;
                    }
                }
                counts.put(s.id, new int[]{attended, total});
                render();
            }

            @Override
            public void onError(String message) {
                lookups.remove(s.id); // Try again on the next update
            }
        });
    }

    // ------------------------------------------------------------------ render

    /** Draws the list again. */
    private void render() {
        if (binding == null || !seedReady) return;
        if (!sessionsReady) {
            Ui.showListState(Ui.ListState.LOADING, binding.txtLoading, binding.empty,
                    binding.listSessions);
            return;
        }
        long now = System.currentTimeMillis();
        List<Session> list = new ArrayList<>();
        for (Session s : sessions.values()) {
            if (matches(s, now)) {
                list.add(s);
            }
        }
        if (list.isEmpty()) {
            if (sessions.isEmpty()) {
                Ui.setEmpty(binding.empty, R.drawable.ic_clock,
                        R.string.history_empty_title, R.string.history_empty_body);
            } else {
                Ui.setEmpty(binding.empty, R.drawable.ic_clock,
                        R.string.history_none_title, R.string.history_none_body);
            }
            Ui.showListState(Ui.ListState.EMPTY, binding.txtLoading, binding.empty,
                    binding.listSessions);
            return;
        }
        Ui.showListState(Ui.ListState.CONTENT, binding.txtLoading, binding.empty,
                binding.listSessions);
        Collections.sort(list, new Comparator<Session>() {
            @Override
            public int compare(Session a, Session b) {
                // Upcoming: the next one first. Other filters: the newest first.
                if (F_UPCOMING.equals(filter)) {
                    return Long.compare(a.start, b.start);
                }
                return Long.compare(b.start, a.start);
            }
        });
        binding.listSessions.removeAllViews();
        for (Session s : list) {
            addRow(s);
        }
    }

    /** True if the session fits the chosen filter. */
    private boolean matches(Session s, long now) {
        if (F_WEEK.equals(filter)) {
            long weekStart = Formats.startOfWeek(now);
            return s.start >= weekStart && s.start < weekStart + SessionJava.ONE_WEEK_MS;
        }
        if (F_UPCOMING.equals(filter)) {
            return s.isOpen() && now < s.start;
        }
        // Future weeks are listed under Upcoming only.
        if (s.isFutureWeek(now)) return false;
        switch (filter) {
            case F_CLASS:
                return Session.TYPE_CLASS.equals(s.type);
            case F_TEST:
                return Session.TYPE_TEST.equals(s.type);
            case F_ACTIVITY:
                return Session.TYPE_SULAM.equals(s.type) || s.isClub();
            default:
                return true;
        }
    }

    /** Adds the row of one session. */
    private void addRow(Session s) {
        ItemHistoryRowBinding row = ItemHistoryRowBinding.inflate(getLayoutInflater(),
                binding.listSessions, false);
        row.rowDay.setText(Formats.dayOfMonth(s.start));
        row.rowMonth.setText(Formats.shortMonth(s.start));
        if (s.isClub()) {
            row.rowTitle.setText(SessionText.join(null, s.title));
        } else {
            row.rowTitle.setText(SessionText.join(SessionText.course(s), s.title));
        }
        if (s.isClub()) {
            row.rowMeta.setText(SessionText.join(SessionText.week(requireContext(), s),
                    SessionText.course(s), SessionText.room(requireContext(), s)));
        } else {
            row.rowMeta.setText(SessionText.join(SessionText.week(requireContext(), s),
                    SessionText.groups(s), SessionText.room(requireContext(), s)));
        }

        if (s.isOpen()) {
            SessionText.statusChip(row.rowChip, s, System.currentTimeMillis());
        } else {
            lookUpCounts(s);
            int[] c = counts.get(s.id);
            if (c == null) {
                row.rowChip.setText(getString(R.string.dash));
            } else if (s.isClub()) {
                row.rowChip.setText(getString(R.string.n_of_m, c[0], c[1]));
            } else if (c[1] == 0) {
                row.rowChip.setText(getString(R.string.dash));
            } else {
                row.rowChip.setText(getString(R.string.percent, Formats.percent(c[0], c[1])));
            }
        }
        row.getRoot().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (s.isOpen()) {
                    LiveMonitorPage.open(requireContext(), s.id);
                } else {
                    openReport.launch(SessionReportPage.intent(requireContext(), s.id, false));
                }
            }
        });
        addLongPress(row, s);
        Ui.addRow(binding.listSessions, row.getRoot());
    }

    /** Long-press: delete a CLOSED session, or cancel a future week. */
    private void addLongPress(ItemHistoryRowBinding row, Session s) {
        if (s.isFutureWeek(System.currentTimeMillis())) {
            row.getRoot().setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showCancelMenu(s);
                    return true;
                }
            });
            ViewCompat.replaceAccessibilityAction(row.getRoot(),
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK,
                    getString(R.string.cancel_this_week), null);
        }
        if (!s.isOpen()) {
            row.getRoot().setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    confirmDelete(s);
                    return true;
                }
            });
            ViewCompat.replaceAccessibilityAction(row.getRoot(),
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK,
                    getString(R.string.delete_session), null);
        }
    }

    /** "Cancel this week" or "Cancel all future weeks". */
    private void showCancelMenu(Session s) {
        String[] options = {getString(R.string.cancel_this_week),
                getString(R.string.cancel_all_weeks)};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(SessionText.join(s.title, SessionText.week(requireContext(), s)))
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        cancelWeeks(s, which == 1);
                    }
                })
                .show();
    }

    /** Cancels this week, or every week of the series that has not started. */
    private void cancelWeeks(Session chosen, boolean all) {
        // SECTION 1: which weeks
        List<Session> weeks = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Session s : sessions.values()) {
            boolean sameSeries = chosen.seriesId.equals(s.seriesId);
            if (s.id.equals(chosen.id) || (all && sameSeries && s.isFutureWeek(now))) {
                weeks.add(s);
            }
        }
        if (weeks.isEmpty()) return;

        // SECTION 2: remove them (the listener removes the rows by itself)
        new SessionJava().cancelWeeks(weeks, new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                if (binding == null) return;
                Snackbar.make(binding.getRoot(), getResources().getQuantityString(
                        R.plurals.weeks_cancelled, weeks.size(), weeks.size()),
                        Snackbar.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });
    }

    /** Asks before deleting a session. */
    private void confirmDelete(Session s) {
        DeleteSessionDialog.show(requireContext(), s, new DeleteSessionDialog.Listener() {
            @Override
            public void onShareCsvFirst() {
                // The CSV is built on the Report page; it opens the share sheet once loaded.
                openReport.launch(SessionReportPage.intent(requireContext(), s.id, true));
            }

            @Override
            public void onDeleted() {
                // The sessionsByLecturer listener removes the row by itself.
                counts.remove(s.id);
                showDeleted();
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });
    }

    /** Shows "Session deleted". */
    private void showDeleted() {
        if (binding != null) {
            Snackbar.make(binding.getRoot(), R.string.session_deleted, Snackbar.LENGTH_LONG).show();
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
