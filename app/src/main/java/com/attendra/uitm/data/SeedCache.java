package com.attendra.uitm.data;

import androidx.annotation.Nullable;

import com.attendra.uitm.model.Club;
import com.attendra.uitm.model.Course;
import com.attendra.uitm.model.Group;
import com.attendra.uitm.model.Programme;
import com.attendra.uitm.model.Venue;
import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Courses, venues, clubs, programmes and groups from the seed data, read once and kept in memory.
 */
public final class SeedCache {

    /** SULAM is done in the final part (Part 6) unless a programme sets its own sulamPart. */
    public static final int DEFAULT_SULAM_PART = 6;

    private static final Map<String, Course> COURSES = new HashMap<>();
    private static final Map<String, Venue> VENUES = new HashMap<>();
    private static final Map<String, Club> CLUBS = new HashMap<>();
    private static final Map<String, Programme> PROGRAMMES = new HashMap<>();
    private static final List<Group> GROUPS = new ArrayList<>();
    private static boolean loaded;

    private SeedCache() {
    }

    /** Reads the three nodes once per app run. */
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
                        readPartsAndTracks(c, course);
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

    /**
     * parts and tracks are read child by child. Asking for the whole parts map would get a list
     * back, since its keys ("2", "3", "5") look like array positions to Firebase.
     */
    private static void readPartsAndTracks(DataSnapshot c, Course course) {
        for (DataSnapshot p : c.child("parts").getChildren()) {
            if (!Boolean.TRUE.equals(p.getValue(Boolean.class))) continue;
            try {
                course.parts.add(Integer.parseInt(p.getKey()));
            } catch (NumberFormatException ignored) {
                // Not a part number, skip it
            }
        }
        for (DataSnapshot t : c.child("tracks").getChildren()) {
            Integer part = t.getValue(Integer.class);
            if (part != null) course.tracks.put(t.getKey(), part);
        }
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
                loadProgrammes(callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private static void loadProgrammes(Callback<Void> callback) {
        new SeedRepository().loadProgrammes(new Callback<List<Programme>>() {
            @Override
            public void onSuccess(List<Programme> programmes) {
                for (Programme p : programmes) PROGRAMMES.put(p.code, p);
                loadGroups(callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private static void loadGroups(Callback<Void> callback) {
        new SeedRepository().loadGroups(new Callback<List<Group>>() {
            @Override
            public void onSuccess(List<Group> groups) {
                GROUPS.clear();
                GROUPS.addAll(groups);
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
    public static Programme programme(@Nullable String code) {
        return code == null ? null : PROGRAMMES.get(code);
    }

    @Nullable
    public static Group group(@Nullable String id) {
        if (id == null) return null;
        for (Group g : GROUPS) if (id.equals(g.id)) return g;
        return null;
    }

    /** Group IDs of one programme and part, sorted (e.g. CDCS2406A, B, C). */
    public static List<String> groupIdsFor(String programme, int part) {
        List<String> ids = new ArrayList<>();
        for (Group g : GROUPS) {
            if (programme != null && programme.equals(g.programme) && g.part == part) ids.add(g.id);
        }
        Collections.sort(ids);
        return ids;
    }

    /** Is this the programme's SULAM course? */
    public static boolean isSulamCourse(@Nullable String courseId) {
        Course course = course(courseId);
        if (course == null) return false;
        Programme p = programme(course.programme);
        if (p != null && p.hasSulamCourse()) {
            // A course code, or the course ID while the code is still TBC.
            return p.sulamCourse.equalsIgnoreCase(course.code) || p.sulamCourse.equals(course.id);
        }
        int part = p != null && p.sulamPart > 0 ? p.sulamPart : DEFAULT_SULAM_PART;
        return course.part == part;
    }

    @Nullable
    public static Course course(@Nullable String id) {
        return id == null ? null : COURSES.get(id);
    }

    /**
     * The courses a lecturer can teach in one programme and part, e.g. CS240 Part 5 (unsorted).
     * A course in several parts (CS259 ACC407 is in 2, 3 and 5) shows up in each of them.
     */
    public static List<Course> coursesFor(String programme, int part) {
        List<Course> list = new java.util.ArrayList<>();
        for (Course c : COURSES.values()) {
            if (programme != null && programme.equals(c.programme) && c.isTeachable() && c.inPart(part)) {
                list.add(c);
            }
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

    /** Every club, sorted by short name (My Clubs Page). */
    public static List<Club> allClubs() {
        List<Club> list = new ArrayList<>(CLUBS.values());
        Collections.sort(list, (a, b) -> String.valueOf(a.shortName)
                .compareToIgnoreCase(String.valueOf(b.shortName)));
        return list;
    }
}
