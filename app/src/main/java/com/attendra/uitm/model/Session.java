package com.attendra.uitm.model;

import com.google.firebase.database.Exclude;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * One attendance session, /sessions/{sessionId} (CLAUDE.md section 7).
 * Times (start, end, lateAfter, createdAt) are milliseconds since 1970.
 */
public class Session {
    public static final String TYPE_CLASS = "class";
    public static final String TYPE_TEST = "test";
    public static final String TYPE_SULAM = "sulam";
    public static final String TYPE_CLUB = "club";
    public static final String STATUS_OPEN = "open";
    public static final String STATUS_CLOSED = "closed";

    /** Node key. Filled in after reading; not stored inside the node. */
    @Exclude
    public String id;

    public String lecturerUid;
    public String type;
    public String title;
    public String courseId;
    public String clubId;
    public Map<String, Boolean> groups;
    /** "building" or "pin" */
    public String where;
    public String building;
    public String venueId;
    /** Pin sessions only: the place picked in search, e.g. "i-City, Seksyen 7, Shah Alam". */
    public String placeName;
    public double lat;
    public double lng;
    public long radius;
    public long start;
    public long end;
    public long lateAfter;
    public String status;
    public long createdAt;

    public Session() {
    }

    @Exclude
    public boolean isOpen() {
        return STATUS_OPEN.equals(status);
    }

    /** Open AND inside its time window, so students can check in right now. */
    @Exclude
    public boolean isCheckInOpen(long now) {
        return isOpen() && now >= start && now < end;
    }

    @Exclude
    public boolean isClub() {
        return TYPE_CLUB.equals(type);
    }

    /** Group IDs in alphabetical order (CDCS2405A, CDCS2405B, ...). */
    @Exclude
    public List<String> groupIds() {
        List<String> ids = new ArrayList<>();
        if (groups != null) {
            for (Map.Entry<String, Boolean> e : groups.entrySet()) {
                if (Boolean.TRUE.equals(e.getValue())) ids.add(e.getKey());
            }
        }
        Collections.sort(ids);
        return ids;
    }
}
