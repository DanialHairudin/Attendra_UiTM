package com.attendra.uitm.ui.lecturer;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.attendra.uitm.R;
import com.attendra.uitm.data.AttendanceJava;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.Db;
import com.attendra.uitm.data.SeedCache;
import com.attendra.uitm.data.TestWorld;
import com.attendra.uitm.databinding.SemesterSummaryPageBinding;
import com.attendra.uitm.databinding.ItemAttendeeRowBinding;
import com.attendra.uitm.model.Attendance;
import com.attendra.uitm.model.Course;
import com.attendra.uitm.ui.BasePage;
import com.attendra.uitm.ui.common.SessionText;
import com.attendra.uitm.ui.common.Ui;
import com.attendra.uitm.util.Csv;
import com.attendra.uitm.util.HomeRouter;
import com.attendra.uitm.util.Names;
import com.attendra.uitm.util.SemesterUtil;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Semester Summary Page: each student's attendance percentage in one course and group. */
// Idea: Moodle Attendance "Report" tab
public class SemesterSummaryPage extends BasePage {

    private static final String EXTRA_COURSE = "course";
    private static final String EXTRA_GROUP = "group";
    private static final String EXTRA_SEMESTER = "semester";

    /** One student in the list. */
    private static final class Row {
        String name = "";
        String studentId = "";
        int present;
        int late;
        int absent;

        /** Present and late both count as attended. */
        int attended() {
            return present + late;
        }

        int total() {
            return present + late + absent;
        }
    }

    private SemesterSummaryPageBinding binding;
    private String courseId;
    private String groupId;
    private String semester;
    private final List<Row> rows = new ArrayList<>();
    private boolean loaded;
    /** The "Below 80%" button is chosen. */
    private boolean belowOnly;

    /** Opens the summary. semester = the label of the teaching class (null = this semester). */
    public static void open(Context from, String courseId, String groupId,
                            @Nullable String semester) {
        from.startActivity(new Intent(from, SemesterSummaryPage.class)
                .putExtra(EXTRA_COURSE, courseId)
                .putExtra(EXTRA_GROUP, groupId)
                .putExtra(EXTRA_SEMESTER, semester));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (FirebaseAuth.getInstance().getUid() == null) {
            HomeRouter.openSplash(this);
            return;
        }
        binding = SemesterSummaryPageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        padForSystemBars(binding.summaryRoot);

        courseId = getIntent().getStringExtra(EXTRA_COURSE);
        groupId = getIntent().getStringExtra(EXTRA_GROUP);
        semester = getIntent().getStringExtra(EXTRA_SEMESTER);
        if (semester == null) {
            semester = SemesterUtil.currentSemester();
        }
        binding.appbar.txtAppbarTitle.setText(R.string.semester_summary);
        binding.chipBelow.setText(getString(R.string.below_limit, Attendance.ATTENDANCE_LIMIT));
        binding.txtSummaryGroup.setText(SessionText.join(groupId, semester));
        setUpButtons();
        loadSeed();
    }

    /** What each button does. */
    private void setUpButtons() {
        binding.appbar.btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        binding.chipBelow.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                belowOnly = checked;
                render();
            }
        });
        binding.btnShareSummary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareCsv();
            }
        });
    }

    // ------------------------------------------------------------- loading

    /** Loads the course name, then the students. */
    private void loadSeed() {
        SeedCache.load(new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                binding.txtSummaryCourse.setText(courseName());
                readStudents();
            }

            @Override
            public void onError(String message) {
                binding.txtSummaryCourse.setText(courseId);
                showError(message);
                readStudents();
            }
        });
    }

    /** SECTION 1: read the students of the group. */
    private void readStudents() {
        Db.readOnce(Db.root().child("studentsByGroup").child(groupId), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                // Keeps tester accounts out of a real lecturer's list.
                TestWorld.forLecturer(TestWorld.keys(snapshot), new Callback<Set<String>>() {
                    @Override
                    public void onSuccess(Set<String> uids) {
                        readNames(new ArrayList<>(uids));
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

    /** Reads each student's name and ID. */
    private void readNames(List<String> uids) {
        List<String> paths = new ArrayList<>();
        for (String uid : uids) {
            paths.add("students/" + uid);
        }
        AttendanceJava.readAll(paths, new Callback<Map<String, DataSnapshot>>() {
            @Override
            public void onSuccess(Map<String, DataSnapshot> profiles) {
                readCounts(uids, profiles);
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });
    }

    /** SECTION 2: read the closed sessions and count present, late and absent. */
    private void readCounts(List<String> uids, Map<String, DataSnapshot> profiles) {
        AttendanceJava.countCourse(FirebaseAuth.getInstance().getUid(), courseId, groupId,
                semester, new Callback<Map<String, int[]>>() {
                    @Override
                    public void onSuccess(Map<String, int[]> counts) {
                        buildRows(uids, profiles, counts);
                    }

                    @Override
                    public void onError(String message) {
                        showError(message);
                    }
                });
    }

    /** SECTION 3: one row per student, sorted by name. */
    private void buildRows(List<String> uids, Map<String, DataSnapshot> profiles,
                           Map<String, int[]> counts) {
        rows.clear();
        for (String uid : uids) {
            Row row = new Row();
            DataSnapshot profile = profiles.get("students/" + uid);
            String name = profile.child("name").getValue(String.class);
            String studentId = profile.child("studentId").getValue(String.class);
            if (name != null) row.name = name;
            if (studentId != null) row.studentId = studentId;
            int[] c = counts.get(uid);
            if (c != null) {
                row.present = c[AttendanceJava.PRESENT];
                row.late = c[AttendanceJava.LATE];
                row.absent = c[AttendanceJava.ABSENT];
            }
            rows.add(row);
        }
        Collections.sort(rows, new Comparator<Row>() {
            @Override
            public int compare(Row a, Row b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        loaded = true;
        render();
    }

    // ------------------------------------------------------------- list

    /** SECTION 4: show the list. */
    private void render() {
        if (!loaded) return;
        // No closed sessions: every student has 0 of 0.
        boolean noSessions = true;
        List<Row> shown = new ArrayList<>();
        for (Row row : rows) {
            if (row.total() > 0) noSessions = false;
            if (!belowOnly || Attendance.belowLimit(row.attended(), row.total())) {
                shown.add(row);
            }
        }
        binding.btnShareSummary.setEnabled(!noSessions);
        binding.listSummaryRows.removeAllViews();
        if (noSessions || shown.isEmpty()) {
            if (noSessions) {
                Ui.setEmpty(binding.emptySummary, R.drawable.ic_chart,
                        R.string.summary_empty_title, R.string.summary_empty_body);
            } else {
                Ui.setEmpty(binding.emptySummary, R.drawable.ic_check,
                        R.string.summary_none_title, R.string.summary_none_body);
            }
            Ui.showListState(Ui.ListState.EMPTY, binding.txtSummaryLoading, binding.emptySummary,
                    binding.listSummaryRows);
            return;
        }
        Ui.showListState(Ui.ListState.CONTENT, binding.txtSummaryLoading, binding.emptySummary,
                binding.listSummaryRows);
        for (Row row : shown) {
            addRow(row);
        }
    }

    /** Adds the row of one student. */
    private void addRow(Row row) {
        ItemAttendeeRowBinding view = ItemAttendeeRowBinding.inflate(getLayoutInflater(),
                binding.listSummaryRows, false);
        view.rowAvatar.setText(Names.initials(row.name));
        view.rowName.setText(row.name);
        boolean below = Attendance.belowLimit(row.attended(), row.total());
        String warning = null;
        if (below) {
            warning = getString(R.string.below_limit, Attendance.ATTENDANCE_LIMIT);
        }
        view.rowMeta.setText(SessionText.join(warning, getString(R.string.n_present, row.present),
                getString(R.string.n_late, row.late), getString(R.string.n_absent, row.absent)));
        if (row.total() == 0) {
            view.rowChip.setText(R.string.dash);
            Ui.tintBadge(view.rowChip, R.color.absent, R.color.absent_tint);
        } else if (below) {
            view.rowChip.setText(getString(R.string.percent,
                    Attendance.percent(row.attended(), row.total())));
            Ui.tintBadge(view.rowChip, R.color.rejected, R.color.rejected_tint);
        } else {
            view.rowChip.setText(getString(R.string.percent,
                    Attendance.percent(row.attended(), row.total())));
            Ui.tintBadge(view.rowChip, R.color.present, R.color.present_tint);
        }
        Ui.addRow(binding.listSummaryRows, view.getRoot());
    }

    // ------------------------------------------------------------- CSV

    /** Writes every student to cache/reports/{name}.csv and opens the share sheet. */
    private void shareCsv() {
        // SECTION 1: build the CSV text
        Csv csv = new Csv().row(getResources().getStringArray(R.array.summary_csv_columns));
        for (Row row : rows) {
            String percent = "";
            if (row.total() > 0) {
                percent = String.valueOf(Attendance.percent(row.attended(), row.total()));
            }
            csv.row(row.name, row.studentId, String.valueOf(row.present), String.valueOf(row.late),
                    String.valueOf(row.absent), percent);
        }

        // SECTION 2: write it to a file
        File dir = new File(getCacheDir(), "reports");
        File file = new File(dir, Csv.fileName(courseName() + "_" + groupId + "_summary"));
        try {
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("mkdirs " + dir);
            try (Writer w = new OutputStreamWriter(new FileOutputStream(file),
                    StandardCharsets.UTF_8)) {
                w.write(csv.toString());
            }
        } catch (IOException e) {
            Log.e("SemesterSummary", "CSV export failed", e);
            showError(getString(R.string.csv_failed));
            return;
        }

        // SECTION 3: open the share sheet
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/csv")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.csv_subject, courseName(), groupId))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, getString(R.string.share_csv)));
    }

    // ------------------------------------------------------------- helpers

    /** "ICT600 - Web Technology", or the course ID if it is not loaded. */
    private String courseName() {
        Course course = SeedCache.course(courseId);
        if (course == null) return courseId;
        return course.label();
    }

    /** Shows an error message. */
    private void showError(String message) {
        if (isFinishing()) return;
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }
}
