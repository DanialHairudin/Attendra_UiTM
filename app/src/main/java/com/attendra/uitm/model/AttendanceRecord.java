package com.attendra.uitm.model;

/**
 * One student's record in attendance/{sessionId}/{studentUid} (CLAUDE.md section 7).
 * time is the server time of the check-in or mark, in milliseconds.
 */
public class AttendanceRecord {
    public static final String METHOD_GPS = "gps";
    public static final String METHOD_MANUAL = "manual";
    /** Filled in as Absent when the session closed. */
    public static final String METHOD_AUTO = "auto";
    /** DEBUG builds only: "Simulate check-in" on the Student Dashboard. */
    public static final String METHOD_DEBUG = "debug";

    public String status;
    public long time;
    /** Metres from the geofence centre (GPS check-ins only). */
    public Double distance;
    public Double accuracy;
    /** Where the check-in fix was (5 decimals, about 1 m). GPS attempts only. */
    public Double lat;
    public Double lng;
    public String method;
    /** Why the lecturer marked it manually. */
    public String reason;
    public String markedBy;

    public AttendanceRecord() {
    }
}
