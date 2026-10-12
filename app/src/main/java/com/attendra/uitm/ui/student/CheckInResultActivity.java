package com.attendra.uitm.ui.student;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.attendra.uitm.R;
import com.attendra.uitm.databinding.ActivityCheckInResultBinding;
import com.attendra.uitm.databinding.ItemInfoRowBinding;
import com.attendra.uitm.model.Attempt;
import com.attendra.uitm.model.Attendance;
import com.attendra.uitm.model.AttendanceRecord;
import com.attendra.uitm.model.Session;
import com.attendra.uitm.ui.BaseActivity;
import com.attendra.uitm.ui.common.SessionText;
import com.attendra.uitm.ui.common.Ui;
import com.attendra.uitm.util.Formats;

/**
 * Check-in Result page (CLAUDE.md section 9): Present, Late, or Rejected with
 * the reason. Also shown when the student already has a record for the
 * session. Everything it shows comes in the Intent, so it reads nothing.
 */
public class CheckInResultActivity extends BaseActivity {

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
        from.startActivity(base(from, s, r.status, r.time)
                .putExtra(EXTRA_ALREADY, true)
                .putExtra(EXTRA_DISTANCE, r.distance != null ? r.distance : -1)
                .putExtra(EXTRA_ACCURACY, r.accuracy != null ? r.accuracy : -1));
    }

    private static Intent base(Context from, Session s, String kind, long time) {
        return new Intent(from, CheckInResultActivity.class)
                .putExtra(EXTRA_KIND, kind)
                .putExtra(EXTRA_SESSION, s.id)
                .putExtra(EXTRA_TITLE, s.title)
                .putExtra(EXTRA_VENUE, venueInSentence(from, s))
                .putExtra(EXTRA_TIME, time)
                .putExtra(EXTRA_RADIUS, (int) s.radius)
                .putExtra(EXTRA_LATE_MIN, (int) ((s.lateAfter - s.start) / 60_000));
    }

    /**
     * The venue as it reads inside "You are 214 m from …": the room or place
     * name, or "the pinned location" for a pin session with no place name.
     */
    private static String venueInSentence(Context c, Session s) {
        boolean unnamedPin = "pin".equals(s.where)
                && (s.placeName == null || s.placeName.isEmpty());
        return unnamedPin ? c.getString(R.string.result_pinned_place) : SessionText.room(c, s);
    }

    private ActivityCheckInResultBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCheckInResultBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        padForSystemBars(binding.resultRoot);

        Intent in = getIntent();
        String kind = in.getStringExtra(EXTRA_KIND);
        String reason = in.getStringExtra(EXTRA_REASON);
        boolean already = in.getBooleanExtra(EXTRA_ALREADY, false);
        String title = in.getStringExtra(EXTRA_TITLE);
        String venue = in.getStringExtra(EXTRA_VENUE);
        long time = in.getLongExtra(EXTRA_TIME, System.currentTimeMillis());
        double distance = in.getDoubleExtra(EXTRA_DISTANCE, -1);
        double accuracy = in.getDoubleExtra(EXTRA_ACCURACY, -1);
        int radius = in.getIntExtra(EXTRA_RADIUS, 0);
        int lateMinutes = in.getIntExtra(EXTRA_LATE_MIN, 15);

        boolean rejected = KIND_REJECTED.equals(kind);
        if (rejected) {
            boolean mock = Attempt.REASON_MOCK.equals(reason);
            show(R.drawable.ic_close, R.color.rejected, R.color.rejected_tint,
                    R.string.status_rejected,
                    getString(mock ? R.string.result_mock_title : R.string.result_outside_title),
                    mock ? getString(R.string.result_mock_body)
                            : getString(R.string.result_outside_body,
                            Math.round(distance), venue, radius));
        } else if (already) {
            boolean attended = Attendance.attended(kind);
            show(attended ? R.drawable.ic_check : R.drawable.ic_close,
                    statusColor(kind), statusTint(kind), statusLabel(kind),
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

        addRow(R.string.result_row_session, title);
        addRow(R.string.result_row_time, Formats.time(time));
        if (distance >= 0) {
            addRow(R.string.result_row_distance, getString(distance > radius
                    ? R.string.result_distance_outside : R.string.result_distance_inside,
                    Math.round(distance), radius));
        }
        if (accuracy >= 0) {
            addRow(R.string.result_row_accuracy,
                    getString(R.string.result_accuracy, Math.round(accuracy)));
        }

        // Try again only makes sense after a rejection.
        binding.btnTryAgain.setVisibility(rejected ? android.view.View.VISIBLE
                : android.view.View.GONE);
        binding.btnTryAgain.setOnClickListener(v -> {
            CheckInActivity.open(this, in.getStringExtra(EXTRA_SESSION));
            finish();
        });
        binding.btnHome.setOnClickListener(v -> finish());
    }

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

    private void addRow(@StringRes int label, String value) {
        ItemInfoRowBinding row = ItemInfoRowBinding.inflate(getLayoutInflater(),
                binding.resultRows, false);
        row.infoLabel.setText(label);
        row.infoValue.setText(value);
        row.infoValue.setGravity(Gravity.END);
        Ui.addRow(binding.resultRows, row.getRoot());
    }

    @ColorRes
    private static int statusColor(String status) {
        if (Attendance.PRESENT.equals(status)) return R.color.present;
        if (Attendance.LATE.equals(status)) return R.color.late;
        return R.color.absent;
    }

    @ColorRes
    private static int statusTint(String status) {
        if (Attendance.PRESENT.equals(status)) return R.color.present_tint;
        if (Attendance.LATE.equals(status)) return R.color.late_tint;
        return R.color.absent_tint;
    }

    @StringRes
    private static int statusLabel(String status) {
        if (Attendance.PRESENT.equals(status)) return R.string.status_present;
        if (Attendance.LATE.equals(status)) return R.string.status_late;
        return R.string.status_absent;
    }
}
