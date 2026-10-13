package com.attendra.uitm.model;

import androidx.annotation.Nullable;

/** One teaching class: teachingAssignments/{lecturerUid}/{courseId}_{groupId}. */
public class TeachingAssignment {
    public String courseId;
    public String groupId;
    /** e.g. "September 2026 – February 2027". */
    @Nullable
    public String semester;
    /** Server time it was first saved. */
    @Nullable
    public Long createdAt;

    public TeachingAssignment() {
    }

    public TeachingAssignment(String courseId, String groupId, @Nullable String semester) {
        this.courseId = courseId;
        this.groupId = groupId;
        this.semester = semester;
    }

    /** The database key, e.g. "CS240_ICT600_CDCS2405A". */
    public static String key(String courseId, String groupId) {
        return courseId + "_" + groupId;
    }

    public String key() {
        return key(courseId, groupId);
    }
}
