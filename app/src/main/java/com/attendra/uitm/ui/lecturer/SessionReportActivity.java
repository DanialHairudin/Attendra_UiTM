package com.attendra.uitm.ui.lecturer;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.attendra.uitm.R;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.SeedCache;
import com.attendra.uitm.data.SessionAttendanceWatcher;
import com.attendra.uitm.databinding.ActivitySessionReportBinding;
import com.attendra.uitm.model.Attendance;
import com.attendra.uitm.model.Attempt;
import com.attendra.uitm.model.AttendanceRecord;
import com.attendra.uitm.model.Session;
import com.attendra.uitm.ui.BaseActivity;
import com.attendra.uitm.ui.common.AttendeeText;
import com.attendra.uitm.ui.common.SessionText;
import com.attendra.uitm.ui.common.Ui;
import com.attendra.uitm.util.Csv;
import com.attendra.uitm.util.Formats;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Session Report Page (opened after Close, or for a closed session):
 * attendance percentage with a bar, Present / Late / Absent / Rejected tiles,
 * the rejected attempts and the absent students. Same live data as the
 * Live Monitor (SessionAttendanceWatcher). "Share CSV" exports the list (C11).
 */
public class SessionReportActivity extends BaseActivity {

    private static final String EXTRA_SESSION = "sessionId";

    public static void open(Context from, String sessionId) {
        from.startActivity(new Intent(from, SessionReportActivity.class)
                .putExtra(EXTRA_SESSION, sessionId));
    }

    private ActivitySessionReportBinding binding;
    private SessionAttendanceWatcher watcher;
    private boolean seedReady;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySessionReportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        padForSystemBars(binding.reportRoot);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnShareCsv.setOnClickListener(v -> shareCsv());
        binding.tiles.labelThird.setText(R.string.status_absent);
        Ui.setEmpty(binding.emptyAbsent, R.drawable.ic_book,
                R.string.report_no_absent_title, R.string.report_no_absent_body);

        watcher = new SessionAttendanceWatcher(getIntent().getStringExtra(EXTRA_SESSION),
                new SessionAttendanceWatcher.Listener() {
                    @Override
                    public void onChanged() {
                        render();
                    }

                    @Override
                    public void onError(String message) {
                        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
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
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });
        watcher.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        watcher.stop();
    }

    private void render() {
        Session s = watcher.session;
        if (s == null || !seedReady) return;
        SessionText.statusChip(binding.txtStatus, s, System.currentTimeMillis());
        binding.txtTitle.setText(s.title);
        binding.txtMeta.setText(SessionText.join(SessionText.course(s), SessionText.groups(s),
                Formats.shortDate(s.start) + ", " + Formats.timeRange(s.start, s.end)));
        binding.btnShareCsv.setEnabled(watcher.ready());
        if (!watcher.ready()) return;

        int present = watcher.count(Attendance.PRESENT);
        int late = watcher.count(Attendance.LATE);
        int absent = watcher.count(Attendance.ABSENT);
        // Everyone who may attend: before closing, "Not yet" students count as not attended.
        int total = watcher.eligible().size();
        int attended = present + late;
        int pct = Formats.percent(attended, total);
        binding.txtPct.setText(total == 0 ? getString(R.string.dash) : getString(R.string.percent, pct));
        binding.txtPctDetail.setText(getString(R.string.report_attended, attended, total));
        binding.barPct.setProgress(pct);

        binding.tiles.nPresent.setText(String.valueOf(present));
        binding.tiles.nLate.setText(String.valueOf(late));
        binding.tiles.nThird.setText(String.valueOf(s.isOpen() ? watcher.notYet().size() : absent));
        binding.tiles.labelThird.setText(s.isOpen() ? R.string.status_not_yet : R.string.status_absent);
        binding.tiles.nRejected.setText(String.valueOf(watcher.rejectedCount()));

        List<Attempt> rejected = watcher.rejectedAttempts();
        binding.groupRejected.setVisibility(rejected.isEmpty() ? View.GONE : View.VISIBLE);
        binding.listRejected.removeAllViews();
        for (Attempt a : rejected) {
            AttendeeText.addRejected(binding.listRejected, watcher.person(a.uid), a);
        }

        // Absent list (or "Not yet" if the session is somehow still open)
        List<String> absentees = new ArrayList<>();
        for (String uid : watcher.eligible()) {
            String status = watcher.statusOf(uid);
            if (status == null || Attendance.ABSENT.equals(status)) absentees.add(uid);
        }
        Collections.sort(absentees, (a, b) -> watcher.person(a).name
                .compareToIgnoreCase(watcher.person(b).name));
        binding.listAbsent.removeAllViews();
        if (absentees.isEmpty()) {
            Ui.showListState(Ui.ListState.EMPTY, binding.txtLoading, binding.emptyAbsent,
                    binding.listAbsent);
            return;
        }
        Ui.showListState(Ui.ListState.CONTENT, binding.txtLoading, binding.emptyAbsent,
                binding.listAbsent);
        for (String uid : absentees) {
            AttendeeText.addStudent(binding.listAbsent, watcher.person(uid),
                    watcher.records == null ? null : watcher.records.get(uid));
        }
    }

    // --------------------------------------------------------------- CSV (C11)

    /**
     * Writes every eligible student (with their status, time, method, distance
     * and reason) to cache/reports/{name}.csv and opens the share sheet. The
     * file is shared through our FileProvider, so no storage permission is
     * needed. Check-in lat/lng are left out on purpose: the file leaves the app.
     */
    private void shareCsv() {
        Session s = watcher.session;
        if (s == null || !watcher.ready()) return;

        Csv csv = new Csv().row(getResources().getStringArray(R.array.csv_columns));
        List<String> uids = new ArrayList<>(watcher.eligible());
        Collections.sort(uids, (a, b) -> watcher.person(a).name
                .compareToIgnoreCase(watcher.person(b).name));
        for (String uid : uids) {
            SessionAttendanceWatcher.Person p = watcher.person(uid);
            AttendanceRecord r = watcher.records == null ? null : watcher.records.get(uid);
            csv.row(p.studentId, p.name, p.groupId,
                    getString(statusLabel(r == null ? null : r.status, s.isOpen())),
                    r == null ? "" : Formats.isoDateTime(r.time),
                    r == null ? "" : r.method,
                    r == null || r.distance == null ? "" : String.valueOf(Math.round(r.distance)),
                    r == null ? "" : r.reason);
        }

        String date = Formats.isoDateTime(s.start).substring(0, 10); // yyyy-MM-dd
        File dir = new File(getCacheDir(), "reports");
        File file = new File(dir, Csv.fileName(SessionText.join(
                SessionText.course(s), s.title, date).replace(" · ", "_")));
        try {
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("mkdirs " + dir);
            try (Writer w = new OutputStreamWriter(new FileOutputStream(file),
                    StandardCharsets.UTF_8)) {
                w.write(csv.toString());
            }
        } catch (IOException e) {
            Log.e("SessionReport", "CSV export failed", e);
            Snackbar.make(binding.getRoot(), R.string.csv_failed, Snackbar.LENGTH_LONG).show();
            return;
        }

        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/csv")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.csv_subject, s.title, date))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, getString(R.string.share_csv)));
    }

    /** Present / Late / Absent, or Not yet while the session is still open. */
    private static int statusLabel(@Nullable String status, boolean open) {
        if (Attendance.PRESENT.equals(status)) return R.string.status_present;
        if (Attendance.LATE.equals(status)) return R.string.status_late;
        if (status == null && open) return R.string.status_not_yet;
        return R.string.status_absent;
    }
}
