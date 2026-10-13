package com.attendra.uitm.ui.lecturer;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.fragment.app.Fragment;

import com.attendra.uitm.R;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.Db;
import com.attendra.uitm.data.SeedCache;
import com.attendra.uitm.data.SessionWatcher;
import com.attendra.uitm.databinding.FragmentHistoryBinding;
import com.attendra.uitm.databinding.ItemHistoryRowBinding;
import com.attendra.uitm.model.Attendance;
import com.attendra.uitm.model.Session;
import com.attendra.uitm.ui.common.SessionText;
import com.attendra.uitm.ui.common.Ui;
import com.attendra.uitm.util.Formats;
import com.google.android.material.chip.Chip;
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

/** Lecturer History tab. */
public class HistoryFragment extends Fragment {

    private static final int MAX_SESSIONS = 200;
    private static final String F_ALL = "all";
    private static final String F_CLASS = "class";
    private static final String F_TEST = "test";
    private static final String F_ACTIVITY = "activity";
    private static final String F_WEEK = "week";

    private FragmentHistoryBinding binding;
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
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == SessionReportActivity.RESULT_DELETED) showDeleted();
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        addFilter(F_ALL, R.string.filter_all);
        addFilter(F_CLASS, R.string.filter_class);
        addFilter(F_TEST, R.string.filter_test);
        addFilter(F_ACTIVITY, R.string.filter_activity);
        addFilter(F_WEEK, R.string.filter_this_week);
    }

    private void addFilter(String value, @StringRes int label) {
        Chip chip = new Chip(requireContext()); // Widget.Attendra.Pill from the theme
        chip.setText(label);
        chip.setCheckable(true);
        chip.setSaveEnabled(false);
        chip.setChecked(value.equals(filter));
        chip.setOnCheckedChangeListener((b, checked) -> {
            if (checked) {
                filter = value;
                render();
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
        if (watcher != null) watcher.stop();
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
                    if (Attendance.attended(rec.child("status").getValue(String.class))) attended++;
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

    private void render() {
        if (binding == null || !seedReady) return;
        if (!sessionsReady) {
            Ui.showListState(Ui.ListState.LOADING, binding.txtLoading, binding.empty,
                    binding.listSessions);
            return;
        }
        long weekStart = Formats.startOfWeek(System.currentTimeMillis());
        List<Session> list = new ArrayList<>();
        for (Session s : sessions.values()) {
            if (matches(s, weekStart)) list.add(s);
        }
        if (list.isEmpty()) {
            Ui.setEmpty(binding.empty, R.drawable.ic_clock,
                    sessions.isEmpty() ? R.string.history_empty_title : R.string.history_none_title,
                    sessions.isEmpty() ? R.string.history_empty_body : R.string.history_none_body);
            Ui.showListState(Ui.ListState.EMPTY, binding.txtLoading, binding.empty,
                    binding.listSessions);
            return;
        }
        Ui.showListState(Ui.ListState.CONTENT, binding.txtLoading, binding.empty,
                binding.listSessions);
        Collections.sort(list, (a, b) -> Long.compare(b.start, a.start));
        binding.listSessions.removeAllViews();
        for (Session s : list) addRow(s);
    }

    private boolean matches(Session s, long weekStart) {
        switch (filter) {
            case F_CLASS:
                return Session.TYPE_CLASS.equals(s.type);
            case F_TEST:
                return Session.TYPE_TEST.equals(s.type);
            case F_ACTIVITY:
                return Session.TYPE_SULAM.equals(s.type) || s.isClub();
            case F_WEEK:
                return s.start >= weekStart;
            default:
                return true;
        }
    }

    private void addRow(Session s) {
        ItemHistoryRowBinding row = ItemHistoryRowBinding.inflate(getLayoutInflater(),
                binding.listSessions, false);
        row.rowDay.setText(Formats.dayOfMonth(s.start));
        row.rowMonth.setText(Formats.shortMonth(s.start));
        row.rowTitle.setText(SessionText.join(s.isClub() ? null : SessionText.course(s), s.title));
        row.rowMeta.setText(SessionText.join(
                s.isClub() ? SessionText.course(s) : SessionText.groups(s),
                SessionText.room(requireContext(), s)));

        if (s.isOpen()) {
            SessionText.statusChip(row.rowChip, s, System.currentTimeMillis());
        } else {
            lookUpCounts(s);
            int[] c = counts.get(s.id);
            row.rowChip.setText(c == null ? getString(R.string.dash)
                    : s.isClub() ? getString(R.string.n_of_m, c[0], c[1])
                    : c[1] == 0 ? getString(R.string.dash)
                    : getString(R.string.percent, Formats.percent(c[0], c[1])));
        }
        row.getRoot().setOnClickListener(v -> {
            if (s.isOpen()) LiveMonitorActivity.open(requireContext(), s.id);
            else openReport.launch(SessionReportActivity.intent(requireContext(), s.id, false));
        });
        // Long-press a CLOSED session to delete it.
        if (!s.isOpen()) {
            row.getRoot().setOnLongClickListener(v -> {
                confirmDelete(s);
                return true;
            });
            ViewCompat.replaceAccessibilityAction(row.getRoot(),
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK,
                    getString(R.string.delete_session), null);
        }
        Ui.addRow(binding.listSessions, row.getRoot());
    }

    private void confirmDelete(Session s) {
        DeleteSessionDialog.show(requireContext(), s, new DeleteSessionDialog.Listener() {
            @Override
            public void onShareCsvFirst() {
                // The CSV is built on the Report page; it opens the share sheet once loaded.
                openReport.launch(SessionReportActivity.intent(requireContext(), s.id, true));
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

    private void showDeleted() {
        if (binding != null) {
            Snackbar.make(binding.getRoot(), R.string.session_deleted, Snackbar.LENGTH_LONG).show();
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
