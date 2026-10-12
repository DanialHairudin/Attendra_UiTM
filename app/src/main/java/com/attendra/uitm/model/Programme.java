package com.attendra.uitm.model;

/**
 * One row of /programmes (seed data), e.g. CS240.
 * Public fields + empty constructor so Firebase can fill it with getValue().
 */
public class Programme {
    public String code;
    public String name;
    public int parts;
    public String faculty;
    /**
     * The part in which this programme's students do SULAM (community service
     * learning). 0 = not set: Part 6 is used (CreateSessionViewModel.DEFAULT_SULAM_PART).
     */
    public int sulamPart;

    public Programme() {
    }

    /** Text shown in the Programme dropdown, e.g. "CS240 · Bachelor of Information Technology (Hons.)". */
    public String label() {
        return code + " · " + name;
    }
}
