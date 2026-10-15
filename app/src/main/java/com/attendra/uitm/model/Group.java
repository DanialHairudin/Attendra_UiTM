package com.attendra.uitm.model;

import androidx.annotation.Nullable;

/** One row of /groups (seed data). */
public class Group {
    /** Node key. */
    public String id;
    public String programme;
    public int part;
    public String letter;
    /** The track the whole group follows, e.g. "BIG_DATA". Null when students pick their own. */
    @Nullable
    public String track;

    public Group() {
    }
}
