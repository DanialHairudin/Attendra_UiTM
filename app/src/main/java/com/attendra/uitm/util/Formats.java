package com.attendra.uitm.util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Dates, times and lists written the way the prototype shows them.
 * The app's text is English, so English month/day names are used.
 */
public final class Formats {

    private Formats() {
    }

    /** "Tuesday, 29 September" */
    public static String dateLine(long time) {
        return new SimpleDateFormat("EEEE, d MMMM", Locale.ENGLISH).format(new Date(time));
    }

    /** "Tue, 29 Sep" */
    public static String shortDate(long time) {
        return new SimpleDateFormat("EEE, d MMM", Locale.ENGLISH).format(new Date(time));
    }

    /** "2:00 PM" */
    public static String time(long time) {
        return new SimpleDateFormat("h:mm a", Locale.ENGLISH).format(new Date(time));
    }

    /** "2:00–3:30 PM", or "11:00 AM–12:30 PM" when the halves differ. */
    public static String timeRange(long start, long end) {
        String a = time(start);
        String b = time(end);
        String amPmA = a.substring(a.length() - 2);
        String amPmB = b.substring(b.length() - 2);
        if (amPmA.equals(amPmB)) a = a.substring(0, a.length() - 3);
        return a + "–" + b;
    }

    /** Midnight at the start of the day that contains `time`. */
    public static long startOfDay(long time) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(time);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** True if both times fall on the same calendar day. */
    public static boolean sameDay(long a, long b) {
        return startOfDay(a) == startOfDay(b);
    }

    /**
     * Groups written like the prototype: "CS2405A, B, C". When the IDs share
     * everything except the last letter, only that letter is repeated.
     */
    public static String groups(List<String> ids) {
        if (ids.isEmpty()) return "";
        String first = ids.get(0);
        String stem = first.substring(0, first.length() - 1);
        List<String> parts = new ArrayList<>();
        parts.add(first);
        for (int i = 1; i < ids.size(); i++) {
            String id = ids.get(i);
            boolean sameStem = id.length() == first.length() && id.startsWith(stem);
            parts.add(sameStem ? id.substring(id.length() - 1) : id);
        }
        return String.join(", ", parts);
    }

    /** Percentage 0–100 rounded, e.g. 46 of 50 → 92. */
    public static int percent(int part, int total) {
        return total == 0 ? 0 : Math.round(part * 100f / total);
    }
}
