package com.attendra.uitm.model;

/** One row of /courses (seed data), e.g. CS240_ICT602. */
public class Course {
    /** Node key, e.g. "CS240_ICT602". Filled in after reading. */
    public String id;
    /** e.g. "ICT602". Some programmes still have "TBC" (CLAUDE.md section 11). */
    public String code;
    public String name;
    public String programme;
    public int part;

    public Course() {
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
