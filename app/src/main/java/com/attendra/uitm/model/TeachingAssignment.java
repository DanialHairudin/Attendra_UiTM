package com.attendra.uitm.model;

import androidx.annotation.Nullable;

/**
 * One teaching class: teachingAssignments/{lecturerUid}/{courseId}_{groupId}.
 * The lecturer copies it from the faculty timetable (C5b).
 */
public class TeachingAssignment {
    public String courseId;
    public String groupId;
    /** e.g. "September 2026 – February 2027". Null for classes saved before C5b. */
    @Nullable
    public String semester;
    /** Server time it was first saved. Null for classes saved before C5b. */
    @Nullable
    public Long createdAt;

    public TeachingAssignment() {
    }

    public TeachingAssignment(String courseId, String groupId, @Nullable String semester) {
        this.courseId = courseId;
        this.groupId = groupId;
        this.semester = semester;
    }

    /** The database key, e.g. "CS240_ICT600_CDCS2405A". The rules check this format. */
    public static String key(String courseId, String groupId) {
        return courseId + "_" + groupId;
    }

    public String key() {
        return key(courseId, groupId);
    }
}
