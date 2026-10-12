package com.attendra.uitm.data;

import com.attendra.uitm.model.Attendance;
import com.attendra.uitm.model.AttendanceRecord;
import com.attendra.uitm.model.Session;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.ServerValue;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Writes made from the Live Monitor (CLAUDE.md sections 5 and 7): manual marks,
 * more time, a new radius, and closing a session. Every write that touches
 * more than one path uses ONE updateChildren, so attendance and the
 * studentAttendance mirror never disagree.
 */
public class AttendanceRepository {

    /**
     * Manual attendance for one student (Manual Attendance Page):
     *   attendance/{sid}/{uid}        {status, time, method: "manual", reason, markedBy}
     *   studentAttendance/{uid}/{sid} {status, time}
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

    /**
     * DEBUG only ("Simulate check-in" on the Student Dashboard): writes the
     * record a real GPS check-in would, without the location checks (C9).
     * Late if now is after lateAfter.
     */
    public void simulateCheckIn(Session s, String studentUid, Callback<String> callback) {
        String status = System.currentTimeMillis() > s.lateAfter
                ? Attendance.LATE : Attendance.PRESENT;
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

    /** "+10 min": moves the closing time later (counting from now if it already passed). */
    public void extendEnd(Session s, long extraMs, Callback<Void> callback) {
        long base = Math.max(s.end, System.currentTimeMillis());
        Map<String, Object> updates = new HashMap<>();
        updates.put("sessions/" + s.id + "/end", base + extraMs);
        Db.write(updates, callback);
    }

    /** Radius sheet: a new geofence radius (30–150 m) for this session. */
    public void setRadius(String sessionId, int radius, Callback<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("sessions/" + sessionId + "/radius", radius);
        Db.write(updates, callback);
    }

    /**
     * Closes a session (the Close button, or auto-close when the end time has
     * passed). In ONE updateChildren:
     *   sessions/{sid}/status = "closed", sessions/{sid}/closedAt = server time
     *   for every eligible student WITHOUT a record:
     *     attendance/{sid}/{uid}        {status: "absent", time, method: "auto"}
     *     studentAttendance/{uid}/{sid} {status: "absent", time}
     * The eligible list and the existing records are read fresh first.
     * onSuccess receives how many students were marked absent.
     */
    public void close(Session s, Callback<Integer> callback) {
        SessionRepository.eligibleStudents(s, new Callback<Set<String>>() {
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
                callback.onSuccess(marked);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
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
