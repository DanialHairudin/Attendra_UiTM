package com.attendra.uitm.model;

import java.util.Map;

/** One row of /programmes (seed data), e.g. CS240. */
public class Programme {
    public String code;
    public String name;
    public int parts;
    public String faculty;
    /** The part in which this programme's students do SULAM (community service learning). */
    public int sulamPart;
    /** The course code that is this programme's SULAM course, e.g. "ICT652" for CS240. */
    public String sulamCourse;
    /** Track ID → label, e.g. BIG_DATA → "Big Data". Null for programmes without tracks. */
    public Map<String, String> tracks;
    /** The first part where students split into tracks (5, or 4 for CS259). 0 if no tracks. */
    public int trackStartPart;

    /** True when sulamCourse names a course (its code, or its ID if the code is TBC). */
    public boolean hasSulamCourse() {
        return sulamCourse != null && !sulamCourse.isEmpty() && !"TBC".equalsIgnoreCase(sulamCourse);
    }

    public Programme() {
    }

    public boolean hasTracks() {
        return tracks != null && !tracks.isEmpty();
    }

    /** Do students of this part belong to a track? */
    public boolean hasTracksIn(int part) {
        return hasTracks() && trackStartPart > 0 && part >= trackStartPart;
    }

    /** "Big Data" for BIG_DATA, or the ID itself if the label is missing. */
    public String trackLabel(String trackId) {
        String label = tracks == null ? null : tracks.get(trackId);
        return label != null ? label : trackId;
    }

    /** Text shown in the Programme dropdown, e.g. "CS240 · Bachelor of Information Technology (Hons.)". */
    public String label() {
        return code + " · " + name;
    }
}
