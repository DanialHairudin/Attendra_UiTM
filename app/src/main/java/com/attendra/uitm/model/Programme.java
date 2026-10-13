package com.attendra.uitm.model;

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

    /** True when sulamCourse holds a real, confirmed course code. */
    public boolean hasSulamCourse() {
        return sulamCourse != null && !sulamCourse.isEmpty() && !"TBC".equalsIgnoreCase(sulamCourse);
    }

    public Programme() {
    }

    /** Text shown in the Programme dropdown, e.g. "CS240 · Bachelor of Information Technology (Hons.)". */
    public String label() {
        return code + " · " + name;
    }
}
