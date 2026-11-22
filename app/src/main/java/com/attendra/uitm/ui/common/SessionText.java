package com.attendra.uitm.ui.common;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.DrawableRes;

import com.attendra.uitm.R;
import com.attendra.uitm.data.SeedCache;
import com.attendra.uitm.model.Club;
import com.attendra.uitm.model.Course;
import com.attendra.uitm.model.Session;
import com.attendra.uitm.model.Venue;
import com.attendra.uitm.util.Formats;

/** Turns a Session into the short texts the dashboards show. */
public final class SessionText {

    private SessionText() {
    }

    /** Course code ("ICT602"), or the course name when the code is TBC, or the club's short name. */
    public static String course(Session s) {
        if (s.isClub()) {
            Club club = SeedCache.club(s.clubId);
            if (club != null) {
                return club.shortName;
            }
            return nullToEmpty(s.clubId);
        }
        Course course = SeedCache.course(s.courseId);
        if (course == null) return nullToEmpty(s.courseId);
        if (course.hasRealCode()) {
            return course.code;
        }
        return course.name;
    }

    /** "Week 3 of 14", or null if the session does not repeat. */
    public static String week(Context c, Session s) {
        if (s.seriesId == null) return null;
        return c.getString(R.string.week_n_of_m, s.weekNo, s.weekTotal);
    }

    /** "CDCS2405A, B, C" (empty for club sessions). */
    public static String groups(Session s) {
        return Formats.groups(s.groupIds());
    }

    /** Where it happens: "DK1, CS1 1st floor" for a room. */
    public static String room(Context c, Session s) {
        if ("pin".equals(s.where)) {
            if (s.placeName != null && !s.placeName.isEmpty()) {
                return s.placeName;
            }
            return c.getString(R.string.room_pin);
        }
        Venue v = SeedCache.venue(s.venueId);
        if (v == null) return nullToEmpty(s.building);
        String place;
        if (v.floor == null || v.floor.isEmpty()) {
            place = v.building;
        } else {
            place = c.getString(R.string.building_floor, v.building, v.floor);
        }
        return c.getString(R.string.room_in_building, v.name, place);
    }

    /** Joins the non-empty parts with " - ". */
    public static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            if (sb.length() > 0) {
                sb.append(" - ");
            }
            sb.append(p);
        }
        return sb.toString();
    }

    /** The icon for the session type. */
    @DrawableRes
    public static int icon(Session s) {
        if (Session.TYPE_TEST.equals(s.type)) return R.drawable.ic_clipboard;
        if (Session.TYPE_CLUB.equals(s.type)) return R.drawable.ic_star;
        if (Session.TYPE_SULAM.equals(s.type)) return R.drawable.ic_pin;
        return R.drawable.ic_book;
    }

    /**
     * Status chip for a list row: closed, or past its end time → "Closed" (grey), not started →
     * "Scheduled" (gold), otherwise "Live" (green).
     */
    public static void statusChip(TextView chip, Session s, long now) {
        if (!s.isOpen() || now >= s.end) {
            chip.setText(R.string.status_closed);
            Ui.tintBadge(chip, R.color.absent, R.color.absent_tint);
        } else if (now < s.start) {
            chip.setText(R.string.status_scheduled);
            Ui.tintBadge(chip, R.color.goldText, R.color.gold_tint);
        } else {
            chip.setText(R.string.status_live);
            Ui.tintBadge(chip, R.color.present, R.color.present_tint);
        }
    }

    /** "" instead of null. */
    private static String nullToEmpty(String s) {
        if (s == null) {
            return "";
        }
        return s;
    }
}
