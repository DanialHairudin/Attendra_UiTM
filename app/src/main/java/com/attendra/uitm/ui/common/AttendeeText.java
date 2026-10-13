package com.attendra.uitm.ui.common;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.attendra.uitm.R;
import com.attendra.uitm.data.SessionAttendanceWatcher;
import com.attendra.uitm.databinding.ItemAttendeeRowBinding;
import com.attendra.uitm.model.Attendance;
import com.attendra.uitm.model.AttendanceRecord;
import com.attendra.uitm.model.Attempt;
import com.attendra.uitm.util.Formats;
import com.attendra.uitm.util.Names;

/** Rows and chips for students on the Live Monitor and the Session Report. */
public final class AttendeeText {

    private AttendeeText() {
    }

    /** Present (green), Late (orange), Absent (grey), Not yet (grey) or Rejected (red). */
    public static void statusChip(android.widget.TextView chip, @Nullable String status) {
        if (Attendance.PRESENT.equals(status)) {
            chip.setText(R.string.status_present);
            Ui.tintBadge(chip, R.color.present, R.color.present_tint);
        } else if (Attendance.LATE.equals(status)) {
            chip.setText(R.string.status_late);
            Ui.tintBadge(chip, R.color.late, R.color.late_tint);
        } else if (Attendance.ABSENT.equals(status)) {
            chip.setText(R.string.status_absent);
            Ui.tintBadge(chip, R.color.absent, R.color.absent_tint);
        } else {
            chip.setText(R.string.status_not_yet);
            Ui.tintBadge(chip, R.color.absent, R.color.absent_tint);
        }
    }

    /** One student row. */
    public static View addStudent(LinearLayout list, SessionAttendanceWatcher.Person p,
                                  @Nullable AttendanceRecord r) {
        Context c = list.getContext();
        ItemAttendeeRowBinding row = ItemAttendeeRowBinding.inflate(
                android.view.LayoutInflater.from(c), list, false);
        row.rowAvatar.setText(Names.initials(p.name));
        row.rowName.setText(p.name);
        String meta;
        if (r == null) {
            meta = SessionText.join(p.studentId, p.groupId);
        } else if (AttendanceRecord.METHOD_MANUAL.equals(r.method)) {
            meta = SessionText.join(p.studentId, Formats.time(r.time),
                    c.getString(R.string.manual_meta, r.reason == null ? "" : r.reason));
        } else if (AttendanceRecord.METHOD_AUTO.equals(r.method)) {
            meta = SessionText.join(p.studentId, p.groupId);
        } else {
            meta = SessionText.join(p.studentId, Formats.time(r.time),
                    r.distance == null ? null : meters(c, r.distance));
        }
        row.rowMeta.setText(meta);
        statusChip(row.rowChip, r == null ? null : r.status);
        Ui.addRow(list, row.getRoot());
        return row.getRoot();
    }

    /** One rejected attempt: "9:05 AM · 214 m away" with a red reason chip. */
    public static View addRejected(LinearLayout list, SessionAttendanceWatcher.Person p,
                                   Attempt a) {
        Context c = list.getContext();
        ItemAttendeeRowBinding row = ItemAttendeeRowBinding.inflate(
                android.view.LayoutInflater.from(c), list, false);
        row.rowAvatar.setText(Names.initials(p.name));
        row.rowName.setText(p.name.isEmpty() ? a.uid : p.name);
        row.rowMeta.setText(SessionText.join(Formats.time(a.time),
                a.distance == null ? null : c.getString(R.string.meters_away,
                        (int) Math.round(a.distance))));
        row.rowChip.setText(reasonLabel(a.reason));
        Ui.tintBadge(row.rowChip, R.color.rejected, R.color.rejected_tint);
        Ui.addRow(list, row.getRoot());
        return row.getRoot();
    }

    /** MOCK_LOCATION → "Mock location", OUTSIDE_RADIUS → "Outside". */
    public static int reasonLabel(@Nullable String reason) {
        if (Attempt.REASON_MOCK.equals(reason)) return R.string.reason_mock;
        if (Attempt.REASON_OUTSIDE.equals(reason)) return R.string.reason_outside;
        return R.string.status_rejected;
    }

    private static String meters(Context c, double m) {
        return c.getString(R.string.meters, (int) Math.round(m));
    }
}
