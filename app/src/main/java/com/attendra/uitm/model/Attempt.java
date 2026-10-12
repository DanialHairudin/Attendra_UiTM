package com.attendra.uitm.model;

/**
 * One check-in attempt in attempts/{sessionId}/{pushId} (CLAUDE.md section 6).
 * Every tap on Check in is logged, accepted or not.
 */
public class Attempt {
    /** reason values for rejected attempts (CLAUDE.md section 6). */
    public static final String REASON_MOCK = "MOCK_LOCATION";
    public static final String REASON_OUTSIDE = "OUTSIDE_RADIUS";

    public String uid;
    public long time;
    /** Attendance.RESULT_ACCEPTED or Attendance.RESULT_REJECTED */
    public String result;
    public String reason;
    public Double distance;
    public Double accuracy;
    /** Where the check-in fix was (5 decimals, about 1 m). GPS attempts only. */
    public Double lat;
    public Double lng;

    public Attempt() {
    }

    public boolean isRejected() {
        return Attendance.RESULT_REJECTED.equals(result);
    }
}
