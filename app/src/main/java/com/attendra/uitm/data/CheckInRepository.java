package com.attendra.uitm.data;

import com.attendra.uitm.model.Attendance;
import com.attendra.uitm.model.AttendanceRecord;
import com.attendra.uitm.model.Session;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.ServerValue;

import java.util.HashMap;
import java.util.Map;

/**
 * Database side of the student's GPS check-in (CLAUDE.md section 6).
 *
 * The location checks themselves (mock, accuracy, distance) run in
 * CheckInActivity. This class only reads what those checks need and writes
 * the result:
 * - accepted:  ONE updateChildren with
 *     attendance/{sid}/{uid}         {status, time, distance, accuracy, method: "gps", lat, lng}
 *     studentAttendance/{uid}/{sid}  {status, time}
 *     attempts/{sid}/{pushId}        {uid, time, result: "accepted", distance, accuracy, lat, lng}
 * - rejected (mock location, outside the radius): only the attempts entry,
 *   with result "rejected" and the reason.
 *
 * lat/lng are the one fix read when the student tapped Check in (step M3), so
 * the lecturer can see where they checked in. They are rounded to 5 decimals
 * (about 1 m). There is no tracking: nothing is read before or after the tap.
 */
public class CheckInRepository {

    /**
     * Is this student allowed into this session? Class/Test/SULAM: the
     * student's group is one of the session's groups. Club: they are in
     * clubMembers/{clubId}.
     */
    public void isEligible(Session s, String uid, Callback<Boolean> callback) {
        if (s.isClub()) {
            Db.readOnce(Db.root().child("clubMembers").child(s.clubId).child(uid),
                    new Callback<DataSnapshot>() {
                        @Override
                        public void onSuccess(DataSnapshot snap) {
                            callback.onSuccess(Boolean.TRUE.equals(snap.getValue(Boolean.class)));
                        }

                        @Override
                        public void onError(String message) {
                            callback.onError(message);
                        }
                    });
            return;
        }
        Db.readOnce(Db.root().child("students").child(uid).child("groupId"),
                new Callback<DataSnapshot>() {
                    @Override
                    public void onSuccess(DataSnapshot snap) {
                        String groupId = snap.getValue(String.class);
                        callback.onSuccess(groupId != null && s.groupIds().contains(groupId));
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
    }

    /** The student's existing record for this session, or null if there is none. */
    public void existingRecord(String sessionId, String uid,
                               Callback<AttendanceRecord> callback) {
        Db.readOnce(Db.root().child("attendance").child(sessionId).child(uid),
                new Callback<DataSnapshot>() {
                    @Override
                    public void onSuccess(DataSnapshot snap) {
                        callback.onSuccess(snap.exists()
                                ? snap.getValue(AttendanceRecord.class) : null);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
    }

    /**
     * Accepted check-in. status (present/late) is worked out by the caller
     * from the server clock; time is the server's own timestamp. The C12
     * rules re-check both against the session's start, end and lateAfter.
     * onSuccess receives the saved server time, read back after the write.
     */
    public void writeAccepted(Session s, String uid, String status, double lat, double lng,
                              double distance, double accuracy, Callback<Long> callback) {
        Map<String, Object> record = new HashMap<>();
        record.put("status", status);
        record.put("time", ServerValue.TIMESTAMP);
        record.put("distance", round1(distance));
        record.put("accuracy", round1(accuracy));
        record.put("method", AttendanceRecord.METHOD_GPS);
        record.put("lat", round5(lat));
        record.put("lng", round5(lng));

        Map<String, Object> mirror = new HashMap<>();
        mirror.put("status", status);
        mirror.put("time", ServerValue.TIMESTAMP);

        Map<String, Object> updates = new HashMap<>();
        updates.put("attendance/" + s.id + "/" + uid, record);
        updates.put("studentAttendance/" + uid + "/" + s.id, mirror);
        updates.put("attempts/" + s.id + "/" + newAttemptKey(s.id),
                attempt(uid, Attendance.RESULT_ACCEPTED, null, lat, lng, distance, accuracy));

        Db.write(updates, new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                // Read the record back to show the exact server time it was saved at.
                existingRecord(s.id, uid, new Callback<AttendanceRecord>() {
                    @Override
                    public void onSuccess(AttendanceRecord saved) {
                        callback.onSuccess(saved != null ? saved.time : System.currentTimeMillis());
                    }

                    @Override
                    public void onError(String message) {
                        callback.onSuccess(System.currentTimeMillis());
                    }
                });
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /**
     * Rejected attempt (MOCK_LOCATION or OUTSIDE_RADIUS): logged in attempts
     * only, so the lecturer sees it on the Live Monitor. distance is null for
     * a mock location (it would mean nothing).
     */
    public void logRejected(Session s, String uid, String reason, double lat, double lng,
                            Double distance, double accuracy, Callback<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("attempts/" + s.id + "/" + newAttemptKey(s.id),
                attempt(uid, Attendance.RESULT_REJECTED, reason, lat, lng, distance, accuracy));
        Db.write(updates, callback);
    }

    private static String newAttemptKey(String sessionId) {
        return Db.root().child("attempts").child(sessionId).push().getKey();
    }

    private static Map<String, Object> attempt(String uid, String result, String reason,
                                               double lat, double lng, Double distance,
                                               double accuracy) {
        Map<String, Object> a = new HashMap<>();
        a.put("uid", uid);
        a.put("time", ServerValue.TIMESTAMP);
        a.put("result", result);
        if (reason != null) a.put("reason", reason);
        if (distance != null) a.put("distance", round1(distance));
        a.put("accuracy", round1(accuracy));
        a.put("lat", round5(lat));
        a.put("lng", round5(lng));
        return a;
    }

    /** 5 decimals of a degree is about 1 m: enough to see where, not more. */
    static double round5(double degrees) {
        return Math.round(degrees * 1e5) / 1e5;
    }

    static double round1(double metres) {
        return Math.round(metres * 10) / 10.0;
    }
}
