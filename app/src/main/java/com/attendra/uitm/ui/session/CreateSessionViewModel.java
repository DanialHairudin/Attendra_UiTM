package com.attendra.uitm.ui.session;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.Db;
import com.attendra.uitm.data.SeedCache;
import com.attendra.uitm.data.UserRepository;
import com.attendra.uitm.model.Building;
import com.attendra.uitm.model.Course;
import com.attendra.uitm.model.Programme;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.model.Session;
import com.attendra.uitm.model.Venue;
import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Everything the Create Session wizard needs, kept across the 5 steps and
 * across screen rotation:
 * - what the lecturer may use: teachingAssignments, advised clubs, buildings
 *   (read once when the wizard opens), plus SeedCache for courses and rooms
 * - the draft being built, step by step
 *
 * The rules from CLAUDE.md section 5 live here (which choices are allowed,
 * which location the geofence uses).
 */
public class CreateSessionViewModel extends ViewModel {

    /** One row of teachingAssignments/{uid}. */
    public static final class Assignment {
        public final String courseId;
        public final String groupId;

        Assignment(String courseId, String groupId) {
            this.courseId = courseId;
            this.groupId = groupId;
        }
    }

    public enum LoadState { LOADING, READY, ERROR }

    public static final int RADIUS_MIN = 30;
    public static final int RADIUS_MAX = 150;
    public static final int RADIUS_DEFAULT = 60;
    /** SULAM is done in the final part (Part 6) unless a programme sets its own sulamPart. */
    public static final int DEFAULT_SULAM_PART = 6;
    /** Map start point when nothing else is known: UiTM Shah Alam main campus. */
    public static final double DEFAULT_LAT = 3.0698;
    public static final double DEFAULT_LNG = 101.5037;

    private final MutableLiveData<LoadState> loadState = new MutableLiveData<>(LoadState.LOADING);
    private String loadError;

    // What the lecturer is allowed to pick
    public final List<Assignment> assignments = new ArrayList<>();
    public final List<String> advisedClubs = new ArrayList<>();
    /** Building ID → building, sorted by ID (CS1, CS2). */
    public final Map<String, Building> buildings = new LinkedHashMap<>();
    /** Programme code → the part its students do SULAM in (from /programmes). */
    public final Map<String, Integer> sulamParts = new HashMap<>();

    // ------------------------------------------------------------ the draft
    public String uid;
    public String type;
    public String title = "";
    /** True once the lecturer typed a title, so we stop suggesting one. */
    public boolean titleEdited;
    public String courseId;
    public final TreeSet<String> groups = new TreeSet<>();
    public String clubId;
    /** Club only: true = inside the faculty (room), false = outside (map pin). */
    public boolean clubInside = true;
    public String building;
    public String venueId;
    public Double pinLat;
    public Double pinLng;
    /** Pin mode: full name of the place picked in search (null if none was picked). */
    public String placeName;
    public int radius = RADIUS_DEFAULT;
    /** Midnight of the chosen day. */
    public long day;
    /** Minutes after midnight. */
    public int startMinute;
    public int endMinute;
    public int lateMinutes = 15; // CLAUDE.md section 5 default
    /** Live eligible-student count from the Participants step, for the Review step. */
    public int eligibleCount;

    public CreateSessionViewModel() {
        // Default time: today, opening now (rounded down to 5 min), closing 1 hour later.
        Calendar c = Calendar.getInstance();
        int nowMinute = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        startMinute = nowMinute - nowMinute % 5;
        endMinute = Math.min(startMinute + 60, 24 * 60 - 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        day = c.getTimeInMillis();
    }

    public LiveData<LoadState> getLoadState() {
        return loadState;
    }

    public String getLoadError() {
        return loadError;
    }

    /** Reads seed data, teachingAssignments, clubAdvisors and buildings once. */
    public void load(String lecturerUid) {
        if (loadState.getValue() == LoadState.READY || uid != null) return;
        uid = lecturerUid;
        SeedCache.load(new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                loadAssignments();
            }

            @Override
            public void onError(String message) {
                fail(message);
            }
        });
    }

    private void loadAssignments() {
        Db.readOnce(Db.root().child("teachingAssignments").child(uid), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                for (DataSnapshot a : snapshot.getChildren()) {
                    String c = a.child("courseId").getValue(String.class);
                    String g = a.child("groupId").getValue(String.class);
                    if (c != null && g != null) assignments.add(new Assignment(c, g));
                }
                Collections.sort(assignments, (x, y) -> {
                    int byCourse = x.courseId.compareTo(y.courseId);
                    return byCourse != 0 ? byCourse : x.groupId.compareTo(y.groupId);
                });
                loadClubs();
            }

            @Override
            public void onError(String message) {
                fail(message);
            }
        });
    }

    private void loadClubs() {
        String role = Registration.ROLE_LECTURER;
        Db.readOnce(UserRepository.clubsQuery(uid, role), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                advisedClubs.addAll(UserRepository.clubIds(snapshot, uid, role));
                loadBuildings();
            }

            @Override
            public void onError(String message) {
                fail(message);
            }
        });
    }

    private void loadBuildings() {
        Db.readOnce(Db.root().child("buildings"), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                for (DataSnapshot b : snapshot.getChildren()) {
                    Building building = b.getValue(Building.class);
                    if (building != null) {
                        building.id = b.getKey();
                        buildings.put(building.id, building);
                    }
                }
                loadSulamParts();
            }

            @Override
            public void onError(String message) {
                fail(message);
            }
        });
    }

    /** programmes/{code}/sulamPart, e.g. CS240 → 6. Programmes without it are not filtered. */
    private void loadSulamParts() {
        Db.readOnce(Db.root().child("programmes"), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                for (DataSnapshot p : snapshot.getChildren()) {
                    Programme programme = p.getValue(Programme.class);
                    if (programme != null && programme.sulamPart > 0) {
                        sulamParts.put(p.getKey(), programme.sulamPart);
                    }
                }
                loadState.setValue(LoadState.READY);
            }

            @Override
            public void onError(String message) {
                fail(message);
            }
        });
    }

    private void fail(String message) {
        loadError = message;
        loadState.setValue(LoadState.ERROR);
    }

    // ---------------------------------------------------------- rules / helpers

    public boolean isType(String t) {
        return t.equals(type);
    }

    /** Picking a new type clears the choices that depend on it. */
    public void setType(String newType) {
        if (newType.equals(type)) return;
        type = newType;
        courseId = null;
        groups.clear();
        clubId = null;
        if (!titleEdited) title = "";
    }

    /**
     * Test and SULAM allow several groups of one course (a combined test, or a
     * SULAM activity for CDCS2406A, B and C together). A Class is exactly one group.
     */
    public boolean multiGroup() {
        return isType(Session.TYPE_TEST) || isType(Session.TYPE_SULAM);
    }

    /** SULAM, or a Club meeting held outside: the geofence is a map pin. */
    public boolean usesPin() {
        return isType(Session.TYPE_SULAM) || (isType(Session.TYPE_CLUB) && !clubInside);
    }

    /** The distinct courses the lecturer teaches, in order (Test step). */
    public List<String> courses() {
        List<String> list = new ArrayList<>();
        for (Assignment a : assignments) if (!list.contains(a.courseId)) list.add(a.courseId);
        return list;
    }

    /**
     * True if the course is in its programme's SULAM part: Part 6 for every
     * programme, unless programmes/{code}/sulamPart says otherwise. Courses in
     * Part 5 and below are never offered for SULAM.
     */
    public boolean isSulamCourse(String courseId) {
        Course course = SeedCache.course(courseId);
        if (course == null) return false;
        Integer part = sulamParts.get(course.programme);
        return course.part == (part != null ? part : DEFAULT_SULAM_PART);
    }

    /** Every assigned class (course + group) the lecturer may use for SULAM. */
    public List<Assignment> sulamClasses() {
        List<Assignment> list = new ArrayList<>();
        for (Assignment a : assignments) if (isSulamCourse(a.courseId)) list.add(a);
        return list;
    }

    /** The lecturer's groups for one course. */
    public List<String> groupsFor(String course) {
        List<String> list = new ArrayList<>();
        for (Assignment a : assignments) if (a.courseId.equals(course)) list.add(a.groupId);
        return list;
    }

    /** Is the Participants step complete? */
    public boolean whoValid() {
        if (title.trim().isEmpty()) return false;
        if (isType(Session.TYPE_CLUB)) return clubId != null;
        return courseId != null && !groups.isEmpty();
    }

    @Nullable
    public Building selectedBuilding() {
        return building == null ? null : buildings.get(building);
    }

    /** Rooms of a building, sorted by `order` (CLAUDE.md section 5). */
    public List<Venue> roomsIn(String buildingId) {
        List<Venue> rooms = new ArrayList<>();
        for (Venue v : SeedCache.venuesIn(buildingId)) rooms.add(v);
        Collections.sort(rooms, (a, b) -> Integer.compare(a.order, b.order));
        return rooms;
    }

    /** Is the Venue step complete (including a building that has a location)? */
    public boolean whereValid() {
        if (usesPin()) return pinLat != null && pinLng != null;
        Building b = selectedBuilding();
        return b != null && b.hasLocation() && venueId != null;
    }

    public long startTime() {
        return day + startMinute * 60_000L;
    }

    public long endTime() {
        return day + endMinute * 60_000L;
    }

    public long lateAfterTime() {
        return startTime() + lateMinutes * 60_000L;
    }

    /** Builds the Session to write. Call only when every step is valid. */
    public Session toSession() {
        Session s = new Session();
        s.lecturerUid = uid;
        s.type = type;
        s.title = title.trim();
        if (isType(Session.TYPE_CLUB)) {
            s.clubId = clubId;
        } else {
            s.courseId = courseId;
            s.groups = new HashMap<>();
            for (String g : groups) s.groups.put(g, true);
        }
        if (usesPin()) {
            s.where = "pin";
            s.lat = pinLat;
            s.lng = pinLng;
            s.placeName = placeName;
        } else {
            Building b = selectedBuilding();
            s.where = "building";
            s.building = building;
            s.venueId = venueId;
            // GPS can't tell rooms apart, so the geofence is the building (section 5).
            s.lat = b.lat;
            s.lng = b.lng;
        }
        s.radius = radius;
        s.start = startTime();
        s.end = endTime();
        s.lateAfter = lateAfterTime();
        s.status = Session.STATUS_OPEN;
        return s;
    }
}
