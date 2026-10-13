package com.attendra.uitm.util;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UiTM academic sessions (semesters), as the labels saved in
 * teachingAssignments/{uid}/{key}/semester.
 */
public final class SemesterUtil {

    /** Calendar months (0 = January) where each session starts. */
    private static final int FIRST_START = Calendar.MARCH;
    private static final int SECOND_START = Calendar.SEPTEMBER;

    private static final Pattern YEAR = Pattern.compile("(\\d{4})");

    private SemesterUtil() {
    }

    /** The session running today. */
    public static String currentSemester() {
        return label(indexOf(Calendar.getInstance()));
    }

    /** For the Semester dropdown: the current session and the next one. */
    public static List<String> options() {
        int now = indexOf(Calendar.getInstance());
        List<String> list = new ArrayList<>();
        list.add(label(now));
        list.add(label(now + 1));
        return list;
    }

    /** True if this assignment counts as the current semester. */
    public static boolean isCurrent(@Nullable String semester) {
        return semester == null || semester.equals(currentSemester());
    }

    /** True for a session that ended before the current one (read-only in the list). */
    public static boolean isPast(@Nullable String semester) {
        int index = parse(semester);
        return index >= 0 && index < indexOf(Calendar.getInstance());
    }

    // Each session gets a number, so sessions can be compared and counted: year * 2 for
    // March–August, year * 2 + 1 for September–February.

    private static int indexOf(Calendar day) {
        int year = day.get(Calendar.YEAR);
        int month = day.get(Calendar.MONTH);
        if (month >= SECOND_START) return year * 2 + 1;
        if (month >= FIRST_START) return year * 2;
        return (year - 1) * 2 + 1; // January–February: still last September's session
    }

    private static String label(int index) {
        int year = index / 2;
        return index % 2 == 0
                ? "March–August " + year
                : "September " + year + " – February " + (year + 1);
    }

    /** The number of a saved label, or -1 if it can't be read. */
    private static int parse(@Nullable String semester) {
        if (semester == null) return -1;
        Matcher m = YEAR.matcher(semester);
        if (!m.find()) return -1;
        int year = Integer.parseInt(m.group(1));
        return semester.startsWith("September") ? year * 2 + 1 : year * 2;
    }
}
