package com.attendra.uitm.model;

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

    private Attendance() {
    }

    /** Present and late both count as attended. */
    public static boolean attended(String status) {
        return PRESENT.equals(status) || LATE.equals(status);
    }
}
