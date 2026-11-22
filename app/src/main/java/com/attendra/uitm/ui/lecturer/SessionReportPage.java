package com.attendra.uitm.ui.lecturer;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.attendra.uitm.R;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.SeedCache;
import com.attendra.uitm.data.SessionAttendanceWatcher;
import com.attendra.uitm.databinding.SessionReportPageBinding;
import com.attendra.uitm.model.Attendance;
import com.attendra.uitm.model.Attempt;
import com.attendra.uitm.model.AttendanceRecord;
import com.attendra.uitm.model.Session;
import com.attendra.uitm.ui.BasePage;
import com.attendra.uitm.ui.common.AttendeeText;
import com.attendra.uitm.ui.common.SessionText;
import com.attendra.uitm.ui.common.Ui;
import com.attendra.uitm.util.Csv;
import com.attendra.uitm.util.Formats;
import com.attendra.uitm.util.SemesterUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Session Report Page (opened after Close, or for a closed session): attendance percentage with a
 * bar, Present / Late / Absent / Rejected tiles, the rejected attempts and the absent students.
 */
public class SessionReportPage extends BasePage {

    private static final String EXTRA_SESSION = "sessionId";
    private static final String EXTRA_SHARE = "shareCsv";
    /** Result for a caller that used intent(): the session was deleted. */
    public static final int RESULT_DELETED = RESULT_FIRST_USER;

    /** Opens the report of one session. */
    public static void open(Context from, String sessionId) {
        from.startActivity(intent(from, sessionId, false));
    }

    /** shareCsv = open the share sheet as soon as the report has loaded ("Share CSV first"). */
    public static Intent intent(Context from, String sessionId, boolean shareCsv) {
        return new Intent(from, SessionReportPage.class)
                .putExtra(EXTRA_SESSION, sessionId)
                .putExtra(EXTRA_SHARE, shareCsv);
    }

    private SessionReportPageBinding binding;
    private SessionAttendanceWatcher watcher;
    private boolean seedReady;
    /** "Share CSV first" from History: share once, when the data is ready. */
    private boolean shareWhenReady;
    /** True while deleting: the listeners lose access to the session, so their errors are expected. */
    private boolean deleting;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = SessionReportPageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        padForSystemBars(binding.reportRoot);

        binding.btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        binding.btnShareCsv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareCsv();
            }
        });
        binding.btnSummary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSummary();
            }
        });
        binding.btnMore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu(v);
            }
        });
        shareWhenReady = savedInstanceState == null
                && getIntent().getBooleanExtra(EXTRA_SHARE, false);
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
                        if (deleting) return;
                        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    /** Opens the semester summary. With more than one group, asks which group first. */
    private void openSummary() {
        Session s = watcher.session;
        if (s == null || s.isClub()) return;
        String semester = SemesterUtil.semesterOf(s.start);
        List<String> groups = s.groupIds();
        if (groups.size() == 1) {
            SemesterSummaryPage.open(this, s.courseId, groups.get(0), semester);
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.semester_summary)
                .setItems(groups.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        SemesterSummaryPage.open(SessionReportPage.this, s.courseId,
                                groups.get(which), semester);
                    }
                })
                .show();
    }

    // ---------------------------------------------------------- delete

    /** Overflow menu: "Delete session" (closed sessions only; the button is hidden otherwise). */
    private void showMenu(View anchor) {
        Session s = watcher.session;
        if (s == null || s.isOpen()) return;
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(R.string.delete_session);
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                confirmDelete(s);
                return true;
            }
        });
        menu.show();
    }

    /** Asks before deleting the session. */
    private void confirmDelete(Session s) {
        DeleteSessionDialog.show(this, s, new DeleteSessionDialog.Listener() {
            @Override
            public void onShareCsvFirst() {
                shareCsv();
            }

            @Override
            public void onDeleted() {
                deleting = true;
                watcher.stop();
                if (getCallingActivity() != null) {
                    setResult(RESULT_DELETED); // History shows "Session deleted"
                } else {
                    Toast.makeText(SessionReportPage.this, R.string.session_deleted,
                            Toast.LENGTH_SHORT).show();
                }
                finish();
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

    /** Draws the whole page again. */
    private void render() {
        Session s = watcher.session;
        if (s == null || !seedReady) return;
        SessionText.statusChip(binding.txtStatus, s, System.currentTimeMillis());
        binding.txtTitle.setText(s.title);
        if (s.isClub()) {
            binding.btnSummary.setVisibility(View.GONE);
        } else {
            binding.btnSummary.setVisibility(View.VISIBLE);
        }
        binding.txtMeta.setText(SessionText.join(SessionText.course(s), SessionText.groups(s),
                Formats.shortDate(s.start) + ", " + Formats.timeRange(s.start, s.end)));
        binding.btnShareCsv.setEnabled(watcher.ready());
        if (s.isOpen()) {
            binding.btnMore.setVisibility(View.GONE);
        } else {
            binding.btnMore.setVisibility(View.VISIBLE);
        }
        if (!watcher.ready()) return;
        if (shareWhenReady) {
            shareWhenReady = false;
            shareCsv();
        }

        renderTotals(s);

        renderRejected();

        renderAbsent();
    }

    /** Percentage, progress bar and the four number tiles. */
    private void renderTotals(Session s) {
        int present = watcher.count(Attendance.PRESENT);
        int late = watcher.count(Attendance.LATE);
        int absent = watcher.count(Attendance.ABSENT);
        // Everyone who may attend: before closing, "Not yet" students count as not attended.
        int total = watcher.eligible().size();
        int attended = present + late;
        int pct = Formats.percent(attended, total);
        if (total == 0) {
            binding.txtPct.setText(getString(R.string.dash));
        } else {
            binding.txtPct.setText(getString(R.string.percent, pct));
        }
        binding.txtPctDetail.setText(getString(R.string.report_attended, attended, total));
        binding.barPct.setProgress(pct);

        binding.tiles.nPresent.setText(String.valueOf(present));
        binding.tiles.nLate.setText(String.valueOf(late));
        if (s.isOpen()) {
            binding.tiles.nThird.setText(String.valueOf(watcher.notYet().size()));
        } else {
            binding.tiles.nThird.setText(String.valueOf(absent));
        }
        if (s.isOpen()) {
            binding.tiles.labelThird.setText(R.string.status_not_yet);
        } else {
            binding.tiles.labelThird.setText(R.string.status_absent);
        }
        binding.tiles.nRejected.setText(String.valueOf(watcher.rejectedCount()));
    }

    /** List of rejected attempts. */
    private void renderRejected() {
        List<Attempt> rejected = watcher.rejectedAttempts();
        if (rejected.isEmpty()) {
            binding.groupRejected.setVisibility(View.GONE);
        } else {
            binding.groupRejected.setVisibility(View.VISIBLE);
        }
        binding.listRejected.removeAllViews();
        for (Attempt a : rejected) {
            AttendeeText.addRejected(binding.listRejected, watcher.person(a.uid), a);
        }
    }

    /** List of absent students, sorted by name. */
    private void renderAbsent() {
        // Absent list (or "Not yet" if the session is somehow still open)
        List<String> absentees = new ArrayList<>();
        for (String uid : watcher.eligible()) {
            String status = watcher.statusOf(uid);
            if (status == null || Attendance.ABSENT.equals(status)) {
                absentees.add(uid);
            }
        }
        Collections.sort(absentees, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return watcher.person(a).name
                        .compareToIgnoreCase(watcher.person(b).name);
            }
        });
        binding.listAbsent.removeAllViews();
        if (absentees.isEmpty()) {
            Ui.showListState(Ui.ListState.EMPTY, binding.txtLoading, binding.emptyAbsent,
                    binding.listAbsent);
            return;
        }
        Ui.showListState(Ui.ListState.CONTENT, binding.txtLoading, binding.emptyAbsent,
                binding.listAbsent);
        for (String uid : absentees) {
            if (watcher.records == null) {
                AttendeeText.addStudent(binding.listAbsent, watcher.person(uid), null);
            } else {
                AttendeeText.addStudent(binding.listAbsent, watcher.person(uid),
                        watcher.records.get(uid));
            }
        }
    }

    // --------------------------------------------------------------- CSV

    /**
     * Writes every eligible student (with their status, time, method, distance and reason) to
     * cache/reports/{name}.csv and opens the share sheet.
     */
    private void shareCsv() {
        Session s = watcher.session;
        if (s == null || !watcher.ready()) return;

        // SECTION 1: build the CSV text
        Csv csv = buildCsv(s);

        // SECTION 2: write it to a file
        String date = Formats.isoDateTime(s.start).substring(0, 10); // yyyy-MM-dd
        File dir = new File(getCacheDir(), "reports");
        File file = new File(dir, Csv.fileName(SessionText.join(
                SessionText.course(s), s.title, date).replace(" - ", "_")));
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

        // SECTION 3: open the share sheet
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/csv")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.csv_subject, s.title, date))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, getString(R.string.share_csv)));
    }

    /** One CSV line per eligible student, sorted by name. */
    private Csv buildCsv(Session s) {
        Csv csv = new Csv().row(getResources().getStringArray(R.array.csv_columns));
        List<String> uids = new ArrayList<>(watcher.eligible());
        Collections.sort(uids, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return watcher.person(a).name
                        .compareToIgnoreCase(watcher.person(b).name);
            }
        });
        for (String uid : uids) {
            SessionAttendanceWatcher.Person p = watcher.person(uid);
            AttendanceRecord r;
            if (watcher.records == null) {
                r = null;
            } else {
                r = watcher.records.get(uid);
            }
            // A student with no record yet has empty columns.
            String status = null;
            String time = "";
            String method = "";
            String distance = "";
            String reason = "";
            if (r != null) {
                status = r.status;
                time = Formats.isoDateTime(r.time);
                method = r.method;
                if (r.distance != null) {
                    distance = String.valueOf(Math.round(r.distance));
                }
                reason = r.reason;
            }
            csv.row(p.studentId, p.name, p.groupId, getString(statusLabel(status, s.isOpen())),
                    time, method, distance, reason);
        }
        return csv;
    }

    /** Present / Late / Absent, or Not yet while the session is still open. */
    private static int statusLabel(@Nullable String status, boolean open) {
        if (Attendance.PRESENT.equals(status)) return R.string.status_present;
        if (Attendance.LATE.equals(status)) return R.string.status_late;
        if (status == null && open) return R.string.status_not_yet;
        return R.string.status_absent;
    }
}
