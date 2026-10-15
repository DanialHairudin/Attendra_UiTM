package com.attendra.uitm.model;

import androidx.annotation.Nullable;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * One row of /courses (seed data), e.g. CS240_ICT602. parts and tracks are read by hand in
 * SeedCache, so Firebase is told to skip them here instead of warning about them for every course.
 */
@IgnoreExtraProperties
public class Course {
    public static final String SLOT_CORE = "CORE";
    public static final String SLOT_UNIVERSITY = "UNIVERSITY";
    public static final String SLOT_ELECTIVE = "ELECTIVE";

    /** Node key, e.g. "CS240_ICT602". */
    public String id;
    /** e.g. "ICT602". Some programmes still have "TBC". */
    public String code;
    public String name;
    public String programme;
    /** The lowest part the course is in. Use {@link #inPart(int)} to check a part. */
    public int part;
    /** CORE, UNIVERSITY or ELECTIVE. Null on old seed data. */
    @Nullable
    public String slotType;
    public int creditHours;
    public boolean isSulam;

    /**
     * Every part the course is in (courses/{id}/parts). Filled in by hand in SeedCache, because
     * Firebase hands back keys like "2", "3", "5" as a list rather than a map.
     */
    @Exclude
    public final TreeSet<Integer> parts = new TreeSet<>();
    /**
     * Track ID → the part that track takes this course in (courses/{id}/tracks), e.g.
     * CREATIVE_IT → 5, IT_INFRASTRUCTURE → 6. Empty for courses that aren't in a track pool.
     */
    @Exclude
    public final Map<String, Integer> tracks = new HashMap<>();

    public Course() {
    }

    /** Is the course taught in this part? Old data without a parts map falls back to part. */
    @Exclude
    public boolean inPart(int p) {
        return parts.isEmpty() ? part == p : parts.contains(p);
    }

    /** Something a lecturer teaches as a class. Old data without a slotType counts too. */
    @Exclude
    public boolean isTeachable() {
        return slotType == null || SLOT_CORE.equals(slotType)
                || SLOT_UNIVERSITY.equals(slotType) || SLOT_ELECTIVE.equals(slotType);
    }

    @Exclude
    public boolean isElective() {
        return SLOT_ELECTIVE.equals(slotType);
    }

    /**
     * Is this one of the track's electives in that part? Always ask with the part: the same course
     * can sit in different parts for different tracks.
     */
    @Exclude
    public boolean inTrack(@Nullable String trackId, int p) {
        Integer trackPart = trackId == null ? null : tracks.get(trackId);
        return trackPart != null && trackPart == p;
    }

    /** False for "TBC" codes, which should not be shown (show the name instead). */
    public boolean hasRealCode() {
        return code != null && !code.isEmpty() && !"TBC".equalsIgnoreCase(code);
    }

    /** "ICT602 · Mobile Technology and Development", or just the name if the code is TBC. */
    public String label() {
        return hasRealCode() ? code + " · " + name : name;
    }
}
