package com.attendra.uitm.ui.student;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.attendra.uitm.R;
import com.attendra.uitm.databinding.CheckInResultPageBinding;
import com.attendra.uitm.databinding.ItemInfoRowBinding;
import com.attendra.uitm.model.Attempt;
import com.attendra.uitm.model.Attendance;
import com.attendra.uitm.model.AttendanceRecord;
import com.attendra.uitm.model.Session;
import com.attendra.uitm.ui.BasePage;
import com.attendra.uitm.ui.common.SessionText;
import com.attendra.uitm.ui.common.Ui;
import com.attendra.uitm.util.Formats;

/** Check-in Result page: Present, Late, or Rejected with the reason. */
public class CheckInResultPage extends BasePage {

    private static final String KIND_REJECTED = "rejected";

    private static final String EXTRA_KIND = "kind";
    private static final String EXTRA_REASON = "reason";
    private static final String EXTRA_ALREADY = "already";
    private static final String EXTRA_SESSION = "session_id";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_VENUE = "venue";
    private static final String EXTRA_TIME = "time";
    private static final String EXTRA_DISTANCE = "distance";
    private static final String EXTRA_RADIUS = "radius";
    private static final String EXTRA_ACCURACY = "accuracy";
    private static final String EXTRA_LATE_MIN = "late_minutes";

    /** Accepted check-in: status is present or late; time is the saved server time. */
    public static void openAccepted(Context from, Session s, String status, long time,
                                    double distance, float accuracy) {
        from.startActivity(base(from, s, status, time)
                .putExtra(EXTRA_DISTANCE, distance)
                .putExtra(EXTRA_ACCURACY, (double) accuracy));
    }

    /** Rejected: MOCK_LOCATION (distance -1, not meaningful) or OUTSIDE_RADIUS. */
    public static void openRejected(Context from, Session s, String reason, double distance,
                                    float accuracy) {
        from.startActivity(base(from, s, KIND_REJECTED, System.currentTimeMillis())
                .putExtra(EXTRA_REASON, reason)
                .putExtra(EXTRA_DISTANCE, distance)
                .putExtra(EXTRA_ACCURACY, (double) accuracy));
    }

    /** The student already has a record (checked in before, or marked by the lecturer). */
    public static void openExisting(Context from, Session s, AttendanceRecord r) {
        // -1 = not known (a manual record has no location).
        double distance = -1;
        double accuracy = -1;
        if (r.distance != null) {
            distance = r.distance;
        }
        if (r.accuracy != null) {
            accuracy = r.accuracy;
        }
        from.startActivity(base(from, s, r.status, r.time)
                .putExtra(EXTRA_ALREADY, true)
                .putExtra(EXTRA_DISTANCE, distance)
                .putExtra(EXTRA_ACCURACY, accuracy));
    }

    /** The Intent with the values every result needs. */
    private static Intent base(Context from, Session s, String kind, long time) {
        return new Intent(from, CheckInResultPage.class)
                .putExtra(EXTRA_KIND, kind)
                .putExtra(EXTRA_SESSION, s.id)
                .putExtra(EXTRA_TITLE, s.title)
                .putExtra(EXTRA_VENUE, venueInSentence(from, s))
                .putExtra(EXTRA_TIME, time)
                .putExtra(EXTRA_RADIUS, (int) s.radius)
                .putExtra(EXTRA_LATE_MIN, (int) ((s.lateAfter - s.start) / 60_000));
    }

    /**
     * The venue as it reads inside "You are 214 m from …": the room or place name, or "the pinned
     * location" for a pin session with no place name.
     */
    private static String venueInSentence(Context c, Session s) {
        boolean unnamedPin = "pin".equals(s.where)
                && (s.placeName == null || s.placeName.isEmpty());
        if (unnamedPin) {
            return c.getString(R.string.result_pinned_place);
        }
        return SessionText.room(c, s);
    }

    private CheckInResultPageBinding binding;

    // What CheckInPage sent in the Intent.
    private String kind;
    private String reason;
    private boolean already;
    private String title;
    private String venue;
    private long time;
    private double distance;
    private double accuracy;
    private int radius;
    private int lateMinutes;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = CheckInResultPageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        padForSystemBars(binding.resultRoot);

        // SECTION 1: read what was sent
        Intent in = getIntent();
        kind = in.getStringExtra(EXTRA_KIND);
        reason = in.getStringExtra(EXTRA_REASON);
        already = in.getBooleanExtra(EXTRA_ALREADY, false);
        title = in.getStringExtra(EXTRA_TITLE);
        venue = in.getStringExtra(EXTRA_VENUE);
        time = in.getLongExtra(EXTRA_TIME, System.currentTimeMillis());
        distance = in.getDoubleExtra(EXTRA_DISTANCE, -1);
        accuracy = in.getDoubleExtra(EXTRA_ACCURACY, -1);
        radius = in.getIntExtra(EXTRA_RADIUS, 0);
        lateMinutes = in.getIntExtra(EXTRA_LATE_MIN, 15);

        // SECTION 2: show it
        boolean rejected = KIND_REJECTED.equals(kind);
        showResult(rejected);
        showRows();

        // SECTION 3: buttons. Try again only makes sense after a rejection.
        if (rejected) {
            binding.btnTryAgain.setVisibility(android.view.View.VISIBLE);
        } else {
            binding.btnTryAgain.setVisibility(android.view.View.GONE);
        }
        setUpButtons(in);
    }

    /** Icon, title and message for this result. */
    private void showResult(boolean rejected) {
        if (rejected) {
            String rejectedTitle;
            String rejectedBody;
            if (Attempt.REASON_MOCK.equals(reason)) {
                rejectedTitle = getString(R.string.result_mock_title);
                rejectedBody = getString(R.string.result_mock_body);
            } else {
                rejectedTitle = getString(R.string.result_outside_title);
                rejectedBody = getString(R.string.result_outside_body, Math.round(distance),
                        venue, radius);
            }
            show(R.drawable.ic_close, R.color.rejected, R.color.rejected_tint,
                    R.string.status_rejected, rejectedTitle, rejectedBody);
        } else if (already) {
            int icon;
            if (Attendance.attended(kind)) {
                icon = R.drawable.ic_check;
            } else {
                icon = R.drawable.ic_close;
            }
            show(icon, statusColor(kind), statusTint(kind), statusLabel(kind),
                    getString(R.string.result_already_title),
                    getString(R.string.result_already_body, title, Formats.time(time)));
        } else if (Attendance.LATE.equals(kind)) {
            show(R.drawable.ic_check, R.color.late, R.color.late_tint, R.string.status_late,
                    getString(R.string.result_late_title),
                    getString(R.string.result_late_body, lateMinutes));
        } else {
            show(R.drawable.ic_check, R.color.present, R.color.present_tint,
                    R.string.status_present, getString(R.string.result_present_title),
                    getString(R.string.result_present_body, title));
        }
    }

    /** Session, time, distance and accuracy rows. */
    private void showRows() {
        addRow(R.string.result_row_session, title);
        addRow(R.string.result_row_time, Formats.time(time));
        if (distance >= 0) {
            if (distance > radius) {
                addRow(R.string.result_row_distance, getString(R.string.result_distance_outside,
                        Math.round(distance), radius));
            } else {
                addRow(R.string.result_row_distance, getString(R.string.result_distance_inside,
                        Math.round(distance), radius));
            }
        }
        if (accuracy >= 0) {
            addRow(R.string.result_row_accuracy,
                    getString(R.string.result_accuracy, Math.round(accuracy)));
        }
    }

    /** What each button does. */
    private void setUpButtons(Intent in) {
        binding.btnTryAgain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CheckInPage.open(CheckInResultPage.this, in.getStringExtra(EXTRA_SESSION));
                finish();
            }
        });
        binding.btnHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    /** Fills the icon, badge, title and message. */
    private void show(@DrawableRes int icon, @ColorRes int color, @ColorRes int tint,
                      @StringRes int badge, String title, String body) {
        binding.resultIcon.setImageResource(icon);
        binding.resultIcon.setColorFilter(ContextCompat.getColor(this, color));
        binding.resultIconBg.setBackgroundTintList(ContextCompat.getColorStateList(this, tint));
        binding.resultBadge.setText(badge);
        Ui.tintBadge(binding.resultBadge, color, tint);
        binding.resultTitle.setText(title);
        binding.resultBody.setText(body);
    }

    /** Adds one row of details. */
    private void addRow(@StringRes int label, String value) {
        ItemInfoRowBinding row = ItemInfoRowBinding.inflate(getLayoutInflater(),
                binding.resultRows, false);
        row.infoLabel.setText(label);
        row.infoValue.setText(value);
        row.infoValue.setGravity(Gravity.END);
        Ui.addRow(binding.resultRows, row.getRoot());
    }

    /** The text colour for a status. */
    @ColorRes
    private static int statusColor(String status) {
        if (Attendance.PRESENT.equals(status)) return R.color.present;
        if (Attendance.LATE.equals(status)) return R.color.late;
        return R.color.absent;
    }

    /** The background colour for a status. */
    @ColorRes
    private static int statusTint(String status) {
        if (Attendance.PRESENT.equals(status)) return R.color.present_tint;
        if (Attendance.LATE.equals(status)) return R.color.late_tint;
        return R.color.absent_tint;
    }

    /** The text for a status. */
    @StringRes
    private static int statusLabel(String status) {
        if (Attendance.PRESENT.equals(status)) return R.string.status_present;
        if (Attendance.LATE.equals(status)) return R.string.status_late;
        return R.string.status_absent;
    }
}
