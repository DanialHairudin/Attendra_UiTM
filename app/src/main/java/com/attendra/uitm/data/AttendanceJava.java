package com.attendra.uitm.data;

import static com.attendra.uitm.AttendraApp.str;

import com.attendra.uitm.R;
import com.attendra.uitm.model.Attendance;
import com.attendra.uitm.model.AttendanceRecord;
import com.attendra.uitm.model.Session;
import com.attendra.uitm.ui.common.SessionText;
import com.attendra.uitm.util.SemesterUtil;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.ServerValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes made from the Live Monitor: manual marks, more time, a new radius, and closing a session.
 */
public class AttendanceJava {

    // ============================================================
    // [CREATE] Lecturer marks a student's attendance by hand
    // ============================================================
    /**
     * Manual attendance for one student (Manual Attendance Page): attendance/{sid}/{uid} {status,
     * time, method: "manual", reason, markedBy} studentAttendance/{uid}/{sid} {status, time}
     */
    public void markManual(String sessionId, String studentUid, String status, String reason,
                           String lecturerUid, Callback<Void> callback) {
        Map<String, Object> record = new HashMap<>();
        record.put("status", status);
        record.put("time", ServerValue.TIMESTAMP);
        record.put("method", AttendanceRecord.METHOD_MANUAL);
        record.put("reason", reason);
        record.put("markedBy", lecturerUid);
        Db.write(withMirror(sessionId, studentUid, status, record), callback);
    }

    // ============================================================
    // [CREATE] Fake check-in for testing only
    // ============================================================
    /**
     * DEBUG only ("Simulate check-in" on the Student Dashboard): writes the record a real GPS
     * check-in would, without the location checks.
     */
    public void simulateCheckIn(Session s, String studentUid, Callback<String> callback) {
        String status;
        if (System.currentTimeMillis() > s.lateAfter) {
            status = Attendance.LATE;
        } else {
            status = Attendance.PRESENT;
        }
        Map<String, Object> record = new HashMap<>();
        record.put("status", status);
        record.put("time", ServerValue.TIMESTAMP);
        record.put("method", AttendanceRecord.METHOD_DEBUG);
        Db.write(withMirror(s.id, studentUid, status, record), new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                callback.onSuccess(status);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    // ============================================================
    // [UPDATE] Make the session end later
    // ============================================================
    /** "+10 min": moves the closing time later (counting from now if it already passed). */
    public void extendEnd(Session s, long extraMs, Callback<Void> callback) {
        long base = Math.max(s.end, System.currentTimeMillis());
        Map<String, Object> updates = new HashMap<>();
        updates.put("sessions/" + s.id + "/end", base + extraMs);
        Db.write(updates, callback);
    }

    // ============================================================
    // [UPDATE] Change the check-in radius
    // ============================================================
    /** Radius sheet: a new geofence radius (30–150 m) for this session. */
    public void setRadius(String sessionId, int radius, Callback<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("sessions/" + sessionId + "/radius", radius);
        Db.write(updates, callback);
    }

    /** True while closeOverdue is working, so Home and History never close the same one twice. */
    private static boolean closingNow;

    // ============================================================
    // [UPDATE] Close every session that is past its end time
    // ============================================================
    /**
     * Run when the lecturer opens Home or History. Closes one session after another and answers
     * how many were closed. Does nothing if no session is overdue.
     */
    public void closeOverdue(Collection<Session> sessions, long now, Callback<Integer> callback) {
        if (closingNow) return;
        List<Session> due = new ArrayList<>();
        for (Session s : sessions) {
            if (s.isOpen() && now >= s.end) {
                due.add(s);
            }
        }
        if (due.isEmpty()) return;
        closingNow = true;
        closeNext(due, 0, 0, null, callback);
    }

    /** Closes number i of the list, then the next one. error = the last error, or null. */
    private void closeNext(List<Session> due, int i, int closed, String error,
                           Callback<Integer> callback) {
        if (i == due.size()) {
            closingNow = false;
            if (closed > 0) {
                callback.onSuccess(closed);
            }
            if (error != null) {
                callback.onError(error);
            }
            return;
        }
        close(due.get(i), new Callback<Integer>() {
            @Override
            public void onSuccess(Integer absent) {
                closeNext(due, i + 1, closed + 1, error, callback);
            }

            @Override
            public void onError(String message) {
                closeNext(due, i + 1, closed, message, callback); // Tried again later
            }
        });
    }

    // ============================================================
    // [UPDATE] Close the session and mark the rest as absent
    // ============================================================
    /** Closes a session (the Close button, or auto-close when the end time has passed). */
    public void close(Session s, Callback<Integer> callback) {
        SessionJava.eligibleStudents(s, new Callback<Set<String>>() {
            @Override
            public void onSuccess(Set<String> eligible) {
                Db.readOnce(Db.root().child("attendance").child(s.id),
                        new Callback<DataSnapshot>() {
                            @Override
                            public void onSuccess(DataSnapshot existing) {
                                writeClose(s, eligible, existing, callback);
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

    /** Closes the session and writes Absent for everyone without a record. */
    private void writeClose(Session s, Set<String> eligible, DataSnapshot existing,
                            Callback<Integer> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("sessions/" + s.id + "/status", Session.STATUS_CLOSED);
        updates.put("sessions/" + s.id + "/closedAt", ServerValue.TIMESTAMP);
        int absent = 0;
        for (String uid : eligible) {
            if (existing.hasChild(uid)) continue;
            Map<String, Object> record = new HashMap<>();
            record.put("status", Attendance.ABSENT);
            record.put("time", ServerValue.TIMESTAMP);
            record.put("method", AttendanceRecord.METHOD_AUTO);
            updates.putAll(withMirror(s.id, uid, Attendance.ABSENT, record));
            absent++;
        }
        final int marked = absent;
        Db.write(updates, new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                // The Absent records are saved, so the percentages can be worked out now.
                alertGroups(s, s.groupIds(), 0, new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess(marked);
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
    // [READ] Count each student's attendance in one course
    // ============================================================
    /** Positions in the counts of countCourse(). */
    public static final int PRESENT = 0;
    public static final int LATE = 1;
    public static final int ABSENT = 2;

    /**
     * Answers uid -> {present, late, absent} for the CLOSED sessions of one course and group that
     * this lecturer held in this semester. Only saved records are counted (closing a session saves
     * Absent), the same as the student's My Attendance page.
     */
    public static void countCourse(String lecturerUid, String courseId, String groupId,
                                   String semester, Callback<Map<String, int[]>> callback) {
        // SECTION 1: the session IDs of the group
        Db.readOnce(Db.root().child("sessionsByGroup").child(groupId), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot index) {
                List<String> paths = new ArrayList<>();
                for (DataSnapshot id : index.getChildren()) {
                    paths.add("sessions/" + id.getKey());
                }
                countSessions(paths, lecturerUid, courseId, semester, callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /** Keeps the closed sessions of the course, then counts their records. */
    private static void countSessions(List<String> sessionPaths, String lecturerUid,
                                      String courseId, String semester,
                                      Callback<Map<String, int[]>> callback) {
        // SECTION 2: read the sessions and keep the right ones
        readAll(sessionPaths, new Callback<Map<String, DataSnapshot>>() {
            @Override
            public void onSuccess(Map<String, DataSnapshot> found) {
                List<String> recordPaths = new ArrayList<>();
                for (DataSnapshot snapshot : found.values()) {
                    Session s = snapshot.getValue(Session.class);
                    if (s != null && Session.STATUS_CLOSED.equals(s.status)
                            && lecturerUid.equals(s.lecturerUid) && courseId.equals(s.courseId)
                            && semester.equals(SemesterUtil.semesterOf(s.start))) {
                        recordPaths.add("attendance/" + snapshot.getKey());
                    }
                }
                countRecords(recordPaths, callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /** Counts the records of each student. */
    private static void countRecords(List<String> recordPaths,
                                     Callback<Map<String, int[]>> callback) {
        // SECTION 3: count
        readAll(recordPaths, new Callback<Map<String, DataSnapshot>>() {
            @Override
            public void onSuccess(Map<String, DataSnapshot> found) {
                Map<String, int[]> counts = new HashMap<>();
                for (DataSnapshot session : found.values()) {
                    for (DataSnapshot record : session.getChildren()) {
                        int[] c = counts.get(record.getKey());
                        if (c == null) {
                            c = new int[3];
                            counts.put(record.getKey(), c);
                        }
                        String status = record.child("status").getValue(String.class);
                        if (Attendance.PRESENT.equals(status)) {
                            c[PRESENT]++;
                        } else if (Attendance.LATE.equals(status)) {
                            c[LATE]++;
                        } else {
                            c[ABSENT]++;
                        }
                    }
                }
                callback.onSuccess(counts);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /** Reads many paths at the same time. Answers path -> snapshot once all are back. */
    public static void readAll(List<String> paths, Callback<Map<String, DataSnapshot>> callback) {
        Map<String, DataSnapshot> found = new HashMap<>();
        if (paths.isEmpty()) {
            callback.onSuccess(found);
            return;
        }
        // Arrays of one, because a variable used inside a callback can't be changed there.
        final int[] left = {paths.size()};
        final boolean[] failed = {false};
        for (String path : paths) {
            Db.readOnce(Db.root().child(path), new Callback<DataSnapshot>() {
                @Override
                public void onSuccess(DataSnapshot snapshot) {
                    found.put(path, snapshot);
                    left[0]--;
                    if (left[0] == 0 && !failed[0]) {
                        callback.onSuccess(found);
                    }
                }

                @Override
                public void onError(String message) {
                    if (failed[0]) return;
                    failed[0] = true;
                    callback.onError(message);
                }
            });
        }
    }

    // ============================================================
    // [CREATE] Warn students who are below the attendance limit
    // ============================================================
    /**
     * Works through the groups of a closed session, one after another. An alert that fails never
     * stops the closing: the session is closed already.
     */
    // Idea: Moodle Attendance "warnings"
    private void alertGroups(Session s, List<String> groups, int i, Runnable done) {
        if (s.isClub() || i == groups.size()) {
            done.run();
            return;
        }
        String groupId = groups.get(i);
        Runnable next = new Runnable() {
            @Override
            public void run() {
                alertGroups(s, groups, i + 1, done);
            }
        };
        Db.readOnce(Db.root().child("studentsByGroup").child(groupId), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot members) {
                TestWorld.filter(TestWorld.keys(members), TestWorld.of(s), new Callback<Set<String>>() {
                    @Override
                    public void onSuccess(Set<String> students) {
                        alertCounts(s, groupId, students, next);
                    }

                    @Override
                    public void onError(String message) {
                        next.run();
                    }
                });
            }

            @Override
            public void onError(String message) {
                next.run();
            }
        });
    }

    /** Counts the course, then reads the last alert of every student in the group. */
    private void alertCounts(Session s, String groupId, Set<String> students, Runnable next) {
        countCourse(s.lecturerUid, s.courseId, groupId, SemesterUtil.semesterOf(s.start),
                new Callback<Map<String, int[]>>() {
                    @Override
                    public void onSuccess(Map<String, int[]> counts) {
                        List<String> paths = new ArrayList<>();
                        for (String uid : students) {
                            paths.add("alerts/" + uid + "/" + s.courseId);
                        }
                        readAll(paths, new Callback<Map<String, DataSnapshot>>() {
                            @Override
                            public void onSuccess(Map<String, DataSnapshot> lastAlerts) {
                                writeAlerts(s, students, counts, lastAlerts, next);
                            }

                            @Override
                            public void onError(String message) {
                                next.run();
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {
                        next.run();
                    }
                });
    }

    /** Saves the alerts. A student is alerted again only if the percentage fell further. */
    private void writeAlerts(Session s, Set<String> students, Map<String, int[]> counts,
                             Map<String, DataSnapshot> lastAlerts, Runnable next) {
        Map<String, Object> updates = new HashMap<>();
        for (String uid : students) {
            int[] c = counts.get(uid);
            if (c == null) continue;
            int attended = c[PRESENT] + c[LATE];
            int total = attended + c[ABSENT];
            int percent = Attendance.percent(attended, total);
            String path = "alerts/" + uid + "/" + s.courseId;
            Long last = lastAlerts.get(path).getValue(Long.class);

            if (!Attendance.belowLimit(attended, total)) {
                if (last != null) {
                    updates.put(path, null); // Back above the limit: a new drop alerts again
                }
            } else if (total >= Attendance.MIN_SESSIONS && (last == null || percent < last)) {
                updates.put(path, percent);
                String key = Db.root().child("notifications").child(uid).push().getKey();
                updates.put("notifications/" + uid + "/" + key,
                        alertValues(s, percent, attended, total));
            }
        }
        if (updates.isEmpty()) {
            next.run();
            return;
        }
        Db.write(updates, new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                next.run();
            }

            @Override
            public void onError(String message) {
                next.run();
            }
        });
    }

    /** The alert as saved in notifications/{uid}/{key}. */
    private static Map<String, Object> alertValues(Session s, int percent, int attended, int total) {
        Map<String, Object> n = new HashMap<>();
        n.put("type", "alert");
        n.put("title", str(R.string.alert_title, Attendance.ATTENDANCE_LIMIT));
        n.put("body", str(R.string.alert_body, SessionText.course(s), percent, attended, total));
        n.put("sessionId", s.id);
        n.put("time", ServerValue.TIMESTAMP);
        n.put("read", false);
        return n;
    }

    /** The record plus its studentAttendance mirror ({status, time}), as update paths. */
    private static Map<String, Object> withMirror(String sessionId, String uid, String status,
                                                  Map<String, Object> record) {
        Map<String, Object> mirror = new HashMap<>();
        mirror.put("status", status);
        mirror.put("time", ServerValue.TIMESTAMP);
        Map<String, Object> updates = new HashMap<>();
        updates.put("attendance/" + sessionId + "/" + uid, record);
        updates.put("studentAttendance/" + uid + "/" + sessionId, mirror);
        return updates;
    }
}
