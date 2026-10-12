package com.attendra.uitm.data;

import androidx.annotation.Nullable;

import com.attendra.uitm.model.Club;
import com.attendra.uitm.model.Course;
import com.attendra.uitm.model.Venue;
import com.google.firebase.database.DataSnapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Courses, venues and clubs from the seed data, read once and kept in memory.
 * They almost never change, so there is no need for live listeners here.
 * Screens call load() first and then look things up by ID.
 */
public final class SeedCache {

    private static final Map<String, Course> COURSES = new HashMap<>();
    private static final Map<String, Venue> VENUES = new HashMap<>();
    private static final Map<String, Club> CLUBS = new HashMap<>();
    private static boolean loaded;

    private SeedCache() {
    }

    /** Reads the three nodes once per app run. Calls back on the main thread. */
    public static void load(Callback<Void> callback) {
        if (loaded) {
            callback.onSuccess(null);
            return;
        }
        Db.readOnce(Db.root().child("courses"), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot courses) {
                for (DataSnapshot c : courses.getChildren()) {
                    Course course = c.getValue(Course.class);
                    if (course != null) {
                        course.id = c.getKey();
                        COURSES.put(course.id, course);
                    }
                }
                loadVenues(callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private static void loadVenues(Callback<Void> callback) {
        Db.readOnce(Db.root().child("venues"), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot venues) {
                for (DataSnapshot v : venues.getChildren()) {
                    Venue venue = v.getValue(Venue.class);
                    if (venue != null) {
                        venue.id = v.getKey();
                        VENUES.put(venue.id, venue);
                    }
                }
                loadClubs(callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private static void loadClubs(Callback<Void> callback) {
        new SeedRepository().loadClubs(new Callback<List<Club>>() {
            @Override
            public void onSuccess(List<Club> clubs) {
                for (Club club : clubs) CLUBS.put(club.id, club);
                loaded = true;
                callback.onSuccess(null);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    @Nullable
    public static Course course(@Nullable String id) {
        return id == null ? null : COURSES.get(id);
    }

    /** The courses of one programme and part, e.g. CS240 Part 5 (unsorted). */
    public static List<Course> coursesFor(String programme, int part) {
        List<Course> list = new java.util.ArrayList<>();
        for (Course c : COURSES.values()) {
            if (programme != null && programme.equals(c.programme) && c.part == part) list.add(c);
        }
        return list;
    }

    @Nullable
    public static Venue venue(@Nullable String id) {
        return id == null ? null : VENUES.get(id);
    }

    /** Every room in one building (unsorted). */
    public static List<Venue> venuesIn(String buildingId) {
        List<Venue> list = new java.util.ArrayList<>();
        for (Venue v : VENUES.values()) {
            if (buildingId != null && buildingId.equals(v.building)) list.add(v);
        }
        return list;
    }

    @Nullable
    public static Club club(@Nullable String id) {
        return id == null ? null : CLUBS.get(id);
    }
}
