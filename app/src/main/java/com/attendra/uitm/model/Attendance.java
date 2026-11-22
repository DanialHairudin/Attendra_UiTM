package com.attendra.uitm.model;

import com.attendra.uitm.util.Formats;

/**
 * Values stored in attendance/{sid}/{uid}, studentAttendance/{uid}/{sid} and
 * attempts/{sid}/{pushId}.
 */
public final class Attendance {

    public static final String PRESENT = "present";
    public static final String LATE = "late";
    public static final String ABSENT = "absent";

    /** attempts/{sid}/{pushId}/result. */
    public static final String RESULT_ACCEPTED = "accepted";
    public static final String RESULT_REJECTED = "rejected";

    /** Students below this percentage get a warning. */
    public static final int ATTENDANCE_LIMIT = 80;
    /** No alert before this many sessions, so one absence in week 1 does not count as 0%. */
    public static final int MIN_SESSIONS = 3;

    private Attendance() {
    }

    /** True if attended / total is below the limit (79.9% is below 80%). */
    public static boolean belowLimit(int attended, int total) {
        return total > 0 && attended * 100 < ATTENDANCE_LIMIT * total;
    }

    /**
     * The percentage without decimals, rounded the same way as My Attendance. A student below the
     * limit never sees the limit itself: 79.6% is shown as 79%, not 80%.
     */
    public static int percent(int attended, int total) {
        int percent = Formats.percent(attended, total);
        if (belowLimit(attended, total) && percent >= ATTENDANCE_LIMIT) {
            percent = ATTENDANCE_LIMIT - 1;
        }
        return percent;
    }

    /** Present and late both count as attended. */
    public static boolean attended(String status) {
        return PRESENT.equals(status) || LATE.equals(status);
    }
}
