package com.attendra.uitm.data;

import static com.attendra.uitm.AttendraApp.str;

import com.attendra.uitm.R;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.model.Session;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.ServerValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Creates attendance sessions. */
public class SessionJava {

    /** 7 days. Weekly sessions are this far apart. */
    public static final long ONE_WEEK_MS = 7L * 24 * 60 * 60 * 1000;

    // ============================================================
    // [CREATE] Create a new attendance session
    // ============================================================
    /**
     * Starts a session with ONE multi-path updateChildren, so the session, its indexes and the
     * notifications are all written or none are.
     */
    public void create(Session s, String title, String body, Callback<String> callback) {
        // A session by the Test Lecturer is marked isTest, so it stays in the test world.
        TestWorld.mine(Registration.ROLE_LECTURER, new Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean test) {
                s.isTest = test;
                eligibleStudents(s, new Callback<Set<String>>() {
                    @Override
                    public void onSuccess(Set<String> students) {
                        write(s, students, title, body, callback);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    // ============================================================
    // [CREATE] Create one session for every chosen week
    // ============================================================
    /** weeks = the ticked weeks, e.g. 1, 2, 3, 5 (week 1 is on the date of s). */
    // Idea: Moodle Attendance, "Repeat the session above as follows"
    public void createSeries(Session s, List<Integer> weeks, String title, String body,
                             Callback<String> callback) {
        TestWorld.mine(Registration.ROLE_LECTURER, new Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean test) {
                s.isTest = test;
                eligibleStudents(s, new Callback<Set<String>>() {
                    @Override
                    public void onSuccess(Set<String> students) {
                        writeSeries(s, weeks, students, title, body, callback);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /** Saves the series and all its sessions with one write. Answers the first session's ID. */
    // Based on: Firebase docs "Read and Write Data on Android" (updateChildren)
    private void writeSeries(Session s, List<Integer> weeks, Set<String> students, String title,
                             String body, Callback<String> callback) {
        // SECTION 1: build the series
        Map<String, Object> updates = new HashMap<>();
        s.seriesId = Db.root().child("sessionSeries").push().getKey();
        s.weekTotal = weeks.size();
        updates.put("sessionSeries/" + s.seriesId, seriesValues(s, weeks));

        // SECTION 2: one normal session per ticked week
        long firstStart = s.start;
        long firstEnd = s.end;
        long firstLate = s.lateAfter;
        String firstId = null;
        for (int i = 0; i < weeks.size(); i++) {
            long later = (weeks.get(i) - 1) * ONE_WEEK_MS;
            s.start = firstStart + later;
            s.end = firstEnd + later;
            s.lateAfter = firstLate + later;
            s.weekNo = i + 1;
            String id = Db.root().child("sessions").push().getKey();
            if (firstId == null) {
                firstId = id;
            }
            addSession(updates, id, s, students, title, body);
        }

        // SECTION 3: save everything with one write (all or nothing)
        final String answer = firstId;
        Db.write(updates, new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                callback.onSuccess(answer);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /** The fields saved in sessionSeries/{seriesId}. */
    private static Map<String, Object> seriesValues(Session s, List<Integer> weeks) {
        int lastWeek = weeks.get(weeks.size() - 1);
        Map<String, Object> skipped = new HashMap<>();
        for (int week = 1; week <= lastWeek; week++) {
            if (!weeks.contains(week)) {
                skipped.put("w" + week, true);
            }
        }
        Map<String, Object> series = new HashMap<>();
        series.put("lecturerUid", s.lecturerUid);
        series.put("title", s.title);
        if (s.isClub()) {
            series.put("clubId", s.clubId);
        } else {
            series.put("courseId", s.courseId);
            series.put("groups", s.groups);
        }
        series.put("where", s.where);
        series.put("startTime", s.start);
        series.put("endTime", s.end);
        series.put("lateAfter", s.lateAfter);
        series.put("weeks", lastWeek);
        series.put("skippedWeeks", skipped);
        series.put("createdAt", ServerValue.TIMESTAMP);
        return series;
    }

    // ============================================================
    // [DELETE] Cancel weeks that have not started
    // ============================================================
    /** Removes future weeks of ONE series, with their notifications. */
    public void cancelWeeks(List<Session> weeks, Callback<Void> callback) {
        eligibleStudents(weeks.get(0), new Callback<Set<String>>() {
            @Override
            public void onSuccess(Set<String> students) {
                Map<String, Object> updates = new HashMap<>();
                for (Session s : weeks) {
                    updates.put("sessions/" + s.id, null); // Null deletes the node
                    updates.put("sessionsByLecturer/" + s.lecturerUid + "/" + s.id, null);
                    if (s.isClub()) {
                        updates.put("sessionsByClub/" + s.clubId + "/" + s.id, null);
                    } else {
                        for (String g : s.groupIds()) {
                            updates.put("sessionsByGroup/" + g + "/" + s.id, null);
                        }
                    }
                    for (String uid : students) {
                        updates.put("notifications/" + uid + "/" + s.id, null);
                    }
                }
                Db.write(updates, callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /** Paths that list who may attend: studentsByGroup/{g} for each group, or clubMembers/{club}. */
    public static List<String> eligibleIndexPaths(Session s) {
        List<String> paths = new ArrayList<>();
        if (s.isClub()) {
            paths.add("clubMembers/" + s.clubId);
        } else {
            for (String g : s.groupIds()) {
                paths.add("studentsByGroup/" + g);
            }
        }
        return paths;
    }

    // ============================================================
    // [READ] Get the students who can join a session
    // ============================================================
    /**
     * Reads, once and fresh, the uids of every student who may attend the session: the group/club
     * members in the same world as the session (real or test).
     */
    public static void eligibleStudents(Session s, Callback<Set<String>> callback) {
        readEligible(eligibleIndexPaths(s), 0, new LinkedHashSet<>(), new Callback<Set<String>>() {
            @Override
            public void onSuccess(Set<String> uids) {
                TestWorld.filter(uids, TestWorld.of(s), callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /** Reads the index nodes one after another and collects the student uids. */
    private static void readEligible(List<String> paths, int i, Set<String> into,
                              Callback<Set<String>> callback) {
        if (i == paths.size()) {
            callback.onSuccess(into);
            return;
        }
        Db.readOnce(Db.root().child(paths.get(i)), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                    into.add(child.getKey());
                }
                readEligible(paths, i + 1, into, callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    // ------------------------------------------------------------ delete

    // ============================================================
    // [DELETE] Delete a session and its records
    // ============================================================
    /** Deletes a CLOSED session for good (the lecturer owns it). */
    public void delete(Session s, Callback<Void> callback) {
        if (!Session.STATUS_CLOSED.equals(s.status)) {
            callback.onError(str(R.string.delete_open_refused));
            return;
        }
        eligibleStudents(s, new Callback<Set<String>>() {
            @Override
            public void onSuccess(Set<String> eligible) {
                Db.readOnce(Db.root().child("attendance").child(s.id), new Callback<DataSnapshot>() {
                    @Override
                    public void onSuccess(DataSnapshot records) {
                        Set<String> withRecord = new LinkedHashSet<>();
                        for (DataSnapshot r : records.getChildren()) {
                            withRecord.add(r.getKey());
                        }
                        Set<String> students = new LinkedHashSet<>(eligible);
                        students.addAll(withRecord);

                        Map<String, Object> updates = new HashMap<>();
                        updates.put("sessions/" + s.id, null);
                        updates.put("sessionsByLecturer/" + s.lecturerUid + "/" + s.id, null);
                        if (s.isClub()) {
                            updates.put("sessionsByClub/" + s.clubId + "/" + s.id, null);
                        } else {
                            for (String g : s.groupIds()) {
                                updates.put("sessionsByGroup/" + g + "/" + s.id, null);
                            }
                        }
                        updates.put("attendance/" + s.id, null);
                        updates.put("attempts/" + s.id, null);
                        for (String uid : withRecord) {
                            updates.put("studentAttendance/" + uid + "/" + s.id, null);
                        }
                        addNotificationPaths(s.id, new ArrayList<>(students), 0, updates, callback);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /** Finds each student's notifications about the session, one student at a time, then writes. */
    private static void addNotificationPaths(String sid, List<String> students, int i,
                                             Map<String, Object> updates, Callback<Void> callback) {
        if (i == students.size()) {
            Db.write(updates, callback);
            return;
        }
        String uid = students.get(i);
        Db.readOnce(Db.root().child("notifications").child(uid)
                .orderByChild("sessionId").equalTo(sid), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot found) {
                for (DataSnapshot n : found.getChildren()) {
                    updates.put("notifications/" + uid + "/" + n.getKey(), null);
                }
                addNotificationPaths(sid, students, i + 1, updates, callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /** Puts the session, its indexes and the notifications in one map and saves the map. */
    private void write(Session s, Set<String> students, String title, String body,
                       Callback<String> callback) {
        // SECTION 1: make the session ID
        // push() makes a unique, time-ordered key on the phone; nothing is sent yet.
        String id = Db.root().child("sessions").push().getKey();
        if (id == null) {
            callback.onError(str(R.string.error_generic));
            return;
        }

        // SECTION 2: the session, its index nodes and its notifications
        Map<String, Object> updates = new HashMap<>();
        addSession(updates, id, s, students, title, body);

        // SECTION 3: save everything with one write
        Db.write(updates, new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                callback.onSuccess(id);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /** Adds one session, its index nodes and one notification per student to the map. */
    private static void addSession(Map<String, Object> updates, String id, Session s,
                                   Set<String> students, String title, String body) {
        updates.put("sessions/" + id, sessionValues(s));
        updates.put("sessionsByLecturer/" + s.lecturerUid + "/" + id, true);
        if (s.isClub()) {
            updates.put("sessionsByClub/" + s.clubId + "/" + id, true);
        } else {
            for (String g : s.groupIds()) {
                updates.put("sessionsByGroup/" + g + "/" + id, true);
            }
        }

        for (String studentUid : students) {
            Map<String, Object> n = new HashMap<>();
            n.put("title", title);
            n.put("body", body);
            n.put("sessionId", id);
            n.put("time", ServerValue.TIMESTAMP);
            n.put("read", false);
            String key = Db.root().child("notifications").child(studentUid).push().getKey();
            if (s.seriesId != null) {
                // Shown when the week starts. The session ID is the key, so cancelling the week
                // can delete it without searching.
                n.put("showAt", s.start);
                key = id;
            }
            updates.put("notifications/" + studentUid + "/" + key, n);
        }
    }

    /** The fields saved in sessions/{id}. */
    private static Map<String, Object> sessionValues(Session s) {
        Map<String, Object> session = new HashMap<>();
        session.put("lecturerUid", s.lecturerUid);
        session.put("type", s.type);
        session.put("title", s.title);
        if (s.isClub()) {
            session.put("clubId", s.clubId);
        } else {
            session.put("courseId", s.courseId);
            session.put("groups", s.groups);
        }
        session.put("where", s.where);
        if ("building".equals(s.where)) {
            session.put("building", s.building);
            session.put("venueId", s.venueId);
        } else if (s.placeName != null && !s.placeName.isEmpty()) {
            // Pin session: the place the lecturer picked in search (optional).
            session.put("placeName", s.placeName);
        }
        session.put("lat", s.lat);
        session.put("lng", s.lng);
        session.put("radius", s.radius);
        session.put("start", s.start);
        session.put("end", s.end);
        session.put("lateAfter", s.lateAfter);
        session.put("status", Session.STATUS_OPEN);
        session.put("createdAt", ServerValue.TIMESTAMP);
        if (s.isTest) {
            session.put("isTest", true);
        }
        if (s.seriesId != null) {
            session.put("seriesId", s.seriesId);
            session.put("weekNo", s.weekNo);
            session.put("weekTotal", s.weekTotal);
        }
        return session;
    }
}
