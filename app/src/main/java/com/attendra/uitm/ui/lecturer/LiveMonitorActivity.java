package com.attendra.uitm.ui.lecturer;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.attendra.uitm.R;
import com.attendra.uitm.data.AttendanceRepository;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.SeedCache;
import com.attendra.uitm.data.SessionAttendanceWatcher;
import com.attendra.uitm.databinding.ActivityLiveMonitorBinding;
import com.attendra.uitm.map.LiveCheckInMap;
import com.attendra.uitm.databinding.SheetManualBinding;
import com.attendra.uitm.databinding.SheetRadiusBinding;
import com.attendra.uitm.model.Attendance;
import com.attendra.uitm.model.Attempt;
import com.attendra.uitm.model.Session;
import com.attendra.uitm.ui.BaseActivity;
import com.attendra.uitm.ui.common.AttendeeText;
import com.attendra.uitm.ui.common.SessionText;
import com.attendra.uitm.ui.common.Ui;
import com.attendra.uitm.util.DropdownAdapter;
import com.attendra.uitm.util.Formats;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Live Monitor Page. */
public class LiveMonitorActivity extends BaseActivity {

    private static final String EXTRA_SESSION = "sessionId";
    private static final long TEN_MINUTES = 10 * 60_000L;

    /** Filter chip values. */
    private static final String FILTER_ALL = "all";
    private static final String FILTER_NOT_YET = "not_yet";

    public static void open(Context from, String sessionId) {
        from.startActivity(new Intent(from, LiveMonitorActivity.class)
                .putExtra(EXTRA_SESSION, sessionId));
    }

    private ActivityLiveMonitorBinding binding;
    private SessionAttendanceWatcher watcher;
    private final AttendanceRepository repo = new AttendanceRepository();
    private String sessionId;
    private boolean seedReady;
    private boolean busy;
    private String filter = FILTER_ALL;
    /** Read-only check-in map under the tiles (step M4). */
    private LiveCheckInMap liveMap;
    /** What the map last drew, so it is only redrawn when something changed. */
    private String drawnVenue;
    private Object drawnAttendance;
    private Object drawnAttempts;
    private int drawnNames = -1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            renderCountdown();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLiveMonitorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        padForSystemBars(binding.liveRoot);

        sessionId = getIntent().getStringExtra(EXTRA_SESSION);
        binding.btnBack.setOnClickListener(v -> finish());
        binding.tiles.labelThird.setText(R.string.status_not_yet);
        Ui.setEmpty(binding.emptyStudents, R.drawable.ic_book,
                R.string.live_empty_title, R.string.live_empty_body);

        addFilter(FILTER_ALL, R.string.filter_all);
        addFilter(Attendance.PRESENT, R.string.status_present);
        addFilter(Attendance.LATE, R.string.status_late);
        addFilter(FILTER_NOT_YET, R.string.status_not_yet);

        binding.btnMark.setOnClickListener(v -> showManualSheet());
        binding.btnMoreTime.setOnClickListener(v -> addTime());
        binding.btnRadius.setOnClickListener(v -> showRadiusSheet());
        binding.btnClose.setOnClickListener(v -> confirmClose());
        binding.btnReport.setOnClickListener(v -> openReport());

        liveMap = new LiveCheckInMap(binding.liveMap.mapLive);
        binding.liveMap.btnShowAll.setOnClickListener(v -> liveMap.showAll());

        watcher = new SessionAttendanceWatcher(sessionId, new SessionAttendanceWatcher.Listener() {
            @Override
            public void onChanged() {
                render();
            }

            @Override
            public void onError(String message) {
                showMessage(message);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        SeedCache.load(new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                seedReady = true;
                render();
            }

            @Override
            public void onError(String message) {
                showMessage(message);
            }
        });
        watcher.start();
        handler.post(tick);
    }

    @Override
    protected void onStop() {
        super.onStop();
        watcher.stop();
        handler.removeCallbacks(tick);
    }

    @Override
    protected void onResume() {
        super.onResume();
        liveMap.onResume();
    }

    @Override
    protected void onPause() {
        liveMap.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        liveMap.onDestroyView();
        super.onDestroy();
    }

    private void addFilter(String value, @StringRes int label) {
        Chip chip = new Chip(this); // Widget.Attendra.Pill from the theme
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

    // ------------------------------------------------------------------ render

    private void render() {
        Session s = watcher.session;
        if (s == null || !seedReady) return;
        boolean closed = !s.isOpen();

        SessionText.statusChip(binding.txtStatus, s, System.currentTimeMillis());
        binding.txtTitle.setText(s.title);
        binding.txtCourse.setText(SessionText.join(SessionText.course(s), SessionText.groups(s)));
        binding.txtRoom.setText(SessionText.join(SessionText.room(this, s),
                getString(R.string.meters_radius, s.radius)));
        renderCountdown();

        // Tiles
        binding.tiles.nPresent.setText(String.valueOf(watcher.count(Attendance.PRESENT)));
        binding.tiles.nLate.setText(String.valueOf(watcher.count(Attendance.LATE)));
        binding.tiles.nThird.setText(String.valueOf(watcher.notYet().size()));
        binding.tiles.nRejected.setText(String.valueOf(watcher.rejectedCount()));

        renderMap(s);
        renderStudents();
        renderRejected();

        binding.barActions.setVisibility(closed ? View.GONE : View.VISIBLE);
        binding.btnReport.setVisibility(closed ? View.VISIBLE : View.GONE);
    }

    /** Eligible students for the chosen filter, sorted by name. */
    private void renderStudents() {
        binding.listStudents.removeAllViews();
        if (!watcher.ready()) {
            Ui.showListState(Ui.ListState.LOADING, binding.txtLoading, binding.emptyStudents,
                    binding.listStudents);
            return;
        }
        List<String> uids = new ArrayList<>();
        for (String uid : watcher.eligible()) {
            String status = watcher.statusOf(uid);
            boolean show = FILTER_ALL.equals(filter)
                    || (FILTER_NOT_YET.equals(filter) && status == null)
                    || filter.equals(status);
            if (show) uids.add(uid);
        }
        if (uids.isEmpty()) {
            Ui.setEmpty(binding.emptyStudents, R.drawable.ic_book,
                    FILTER_ALL.equals(filter) ? R.string.live_empty_title : R.string.live_none_title,
                    FILTER_ALL.equals(filter) ? R.string.live_empty_body : R.string.live_none_body);
            Ui.showListState(Ui.ListState.EMPTY, binding.txtLoading, binding.emptyStudents,
                    binding.listStudents);
            return;
        }
        Ui.showListState(Ui.ListState.CONTENT, binding.txtLoading, binding.emptyStudents,
                binding.listStudents);
        Collections.sort(uids, (a, b) -> watcher.person(a).name
                .compareToIgnoreCase(watcher.person(b).name));
        for (String uid : uids) {
            View row = AttendeeText.addStudent(binding.listStudents, watcher.person(uid),
                    watcher.records == null ? null : watcher.records.get(uid));
            row.setOnClickListener(v -> focusOnMap(uid));
        }
    }

    private void renderRejected() {
        List<Attempt> rejected = watcher.rejectedAttempts();
        binding.groupRejected.setVisibility(rejected.isEmpty() ? View.GONE : View.VISIBLE);
        binding.listRejected.removeAllViews();
        for (Attempt a : rejected) {
            View row = AttendeeText.addRejected(binding.listRejected, watcher.person(a.uid), a);
            row.setOnClickListener(v -> focusOnMap(a.uid));
        }
    }

    /**
     * Check-in map (step M4): the venue circle, and one dot per student where they checked in
     * (green present, orange late, red rejected).
     */
    private void renderMap(Session s) {
        String venue = s.lat + "," + s.lng + "," + s.radius;
        if (!venue.equals(drawnVenue)) {
            drawnVenue = venue;
            liveMap.setVenue(s.lat, s.lng, s.radius);
        }
        DataSnapshot attendance = watcher.attendanceSnapshot;
        DataSnapshot attempts = watcher.attemptsSnapshot;
        if (attendance == null || attempts == null) return;
        if (attendance == drawnAttendance && attempts == drawnAttempts
                && watcher.people.size() == drawnNames) return;
        drawnAttendance = attendance;
        drawnAttempts = attempts;
        drawnNames = watcher.people.size();

        Map<String, String> names = new HashMap<>();
        for (Map.Entry<String, SessionAttendanceWatcher.Person> e : watcher.people.entrySet()) {
            names.put(e.getKey(), e.getValue().name);
        }
        liveMap.setPoints(LiveCheckInMap.fromSnapshots(attendance, attempts, names));
    }

    /** A student row was tapped: scroll up to the map and fly to their dot. */
    private void focusOnMap(String uid) {
        if (liveMap.focusStudent(uid)) {
            binding.scrollLive.smoothScrollTo(0, binding.liveMap.getRoot().getTop());
        } else {
            showMessage(getString(R.string.live_map_no_location));
        }
    }

    /** "14:32 left to check in", "Opens 9:00 AM", or "0:00 Check-in time is over". */
    private void renderCountdown() {
        Session s = watcher.session;
        if (s == null) return;
        long now = System.currentTimeMillis();
        if (!s.isOpen()) {
            binding.txtCountdown.setText(R.string.dash);
            binding.txtCountdownLabel.setText(getString(R.string.closed_at, Formats.time(s.end)));
        } else if (now < s.start) {
            binding.txtCountdown.setText(Formats.time(s.start));
            binding.txtCountdownLabel.setText(R.string.countdown_opens);
        } else if (now < s.end) {
            binding.txtCountdown.setText(clock(s.end - now));
            binding.txtCountdownLabel.setText(R.string.countdown_left);
        } else {
            binding.txtCountdown.setText(clock(0));
            binding.txtCountdownLabel.setText(R.string.countdown_over);
        }
    }

    /** 872 000 ms → "14:32"; over an hour → "1:05:20". */
    private static String clock(long ms) {
        long sec = Math.max(0, (ms + 999) / 1000);
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
                : String.format(Locale.ROOT, "%d:%02d", m, s);
    }

    // ----------------------------------------------------------------- actions

    /** Manual Attendance Page: a student from "Not yet", Present or Late, and a reason. */
    private void showManualSheet() {
        List<String> notYet = watcher.notYet();
        if (notYet.isEmpty()) {
            showMessage(getString(R.string.manual_nobody));
            return;
        }
        Collections.sort(notYet, (a, b) -> watcher.person(a).name
                .compareToIgnoreCase(watcher.person(b).name));

        BottomSheetDialog sheet = new BottomSheetDialog(this);
        SheetManualBinding b = SheetManualBinding.inflate(getLayoutInflater());
        final String[] chosen = {null};
        DropdownAdapter<String> adapter = new DropdownAdapter<>(this, notYet, uid -> {
            SessionAttendanceWatcher.Person p = watcher.person(uid);
            return SessionText.join(p.name, p.studentId);
        });
        b.inputStudent.setAdapter(adapter);
        b.inputStudent.setOnItemClickListener((parent, v, position, id) -> {
            chosen[0] = (String) parent.getItemAtPosition(position);
            b.layoutStudent.setError(null);
        });
        b.btnSave.setOnClickListener(v -> {
            CharSequence typed = b.inputReason.getText();
            String reason = typed == null ? "" : typed.toString().trim();
            b.layoutStudent.setError(chosen[0] == null ? getString(R.string.manual_pick_student) : null);
            b.layoutReason.setError(reason.isEmpty() ? getString(R.string.manual_reason_required) : null);
            if (chosen[0] == null || reason.isEmpty()) return;

            FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
            if (me == null) return;
            String status = b.chipLate.isChecked() ? Attendance.LATE : Attendance.PRESENT;
            b.btnSave.setEnabled(false);
            repo.markManual(sessionId, chosen[0], status, reason, me.getUid(),
                    new Callback<Void>() {
                        @Override
                        public void onSuccess(Void unused) {
                            sheet.dismiss();
                            showMessage(getString(R.string.manual_saved,
                                    watcher.person(chosen[0]).name));
                        }

                        @Override
                        public void onError(String message) {
                            b.btnSave.setEnabled(true);
                            Toast.makeText(LiveMonitorActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    });
        });
        sheet.setContentView(b.getRoot());
        sheet.show();
    }

    /** +10 min on the closing time. */
    private void addTime() {
        Session s = watcher.session;
        if (s == null || busy) return;
        busy = true;
        repo.extendEnd(s, TEN_MINUTES, new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                busy = false;
                Session now = watcher.session;
                if (now != null) {
                    showMessage(getString(R.string.more_time_done, Formats.time(now.end)));
                }
            }

            @Override
            public void onError(String message) {
                busy = false;
                showMessage(message);
            }
        });
    }

    /** Radius sheet: 30–150 m, saved straight into sessions/{sid}/radius. */
    private void showRadiusSheet() {
        Session s = watcher.session;
        if (s == null) return;
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        SheetRadiusBinding b = SheetRadiusBinding.inflate(getLayoutInflater());
        int start = (int) Math.max(30, Math.min(150, s.radius - s.radius % 5));
        b.sliderRadius.setValue(start);
        b.txtRadius.setText(getString(R.string.meters, start));
        b.sliderRadius.addOnChangeListener((slider, value, fromUser) ->
                b.txtRadius.setText(getString(R.string.meters, Math.round(value))));
        b.btnSave.setOnClickListener(v -> {
            b.btnSave.setEnabled(false);
            repo.setRadius(sessionId, Math.round(b.sliderRadius.getValue()), new Callback<Void>() {
                @Override
                public void onSuccess(Void unused) {
                    sheet.dismiss();
                }

                @Override
                public void onError(String message) {
                    b.btnSave.setEnabled(true);
                    Toast.makeText(LiveMonitorActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        });
        sheet.setContentView(b.getRoot());
        sheet.show();
    }

    private void confirmClose() {
        if (watcher.session == null) return;
        int notYet = watcher.notYet().size();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.close_title)
                .setMessage(getResources().getQuantityString(R.plurals.close_body, notYet, notYet))
                .setPositiveButton(R.string.live_close, (d, w) -> closeSession())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Close: status "closed" + Absent for everyone without a record, then the report. */
    private void closeSession() {
        Session s = watcher.session;
        if (s == null || busy) return;
        busy = true;
        binding.btnClose.setEnabled(false);
        repo.close(s, new Callback<Integer>() {
            @Override
            public void onSuccess(Integer absent) {
                busy = false;
                openReport();
            }

            @Override
            public void onError(String message) {
                busy = false;
                binding.btnClose.setEnabled(true);
                showMessage(message);
            }
        });
    }

    private void openReport() {
        SessionReportActivity.open(this, sessionId);
        finish();
    }

    private void showMessage(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG)
                .setAnchorView(binding.barActions.getVisibility() == View.VISIBLE
                        ? binding.barActions : binding.btnReport)
                .show();
    }
}
