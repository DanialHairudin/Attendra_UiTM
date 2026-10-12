package com.attendra.uitm.data;

import static com.attendra.uitm.AttendraApp.str;

import com.attendra.uitm.R;
import com.attendra.uitm.model.Session;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.ServerValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Creates attendance sessions (CLAUDE.md sections 5 and 7). */
public class SessionRepository {

    /**
     * Starts a session with ONE multi-path updateChildren, so the session, its
     * indexes and the notifications are all written or none are:
     *
     *   sessions/{id}                          the session itself
     *   sessionsByLecturer/{uid}/{id}          → Lecturer Dashboard
     *   sessionsByGroup/{group}/{id}           → Student Dashboard (each group), or
     *   sessionsByClub/{club}/{id}             → Student Dashboard (club members)
     *   notifications/{studentUid}/{pushId}    one per eligible student (in-app only)
     *
     * The eligible students are read fresh right before writing, so a student
     * who registered a minute ago still gets the notification.
     *
     * @param s       the session to write (s.id is ignored; a new push ID is used)
     * @param title   notification title shown to students
     * @param body    notification body shown to students
     * onSuccess receives the new session ID.
     */
    public void create(Session s, String title, String body, Callback<String> callback) {
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

    /** Paths that list who may attend: studentsByGroup/{g} for each group, or clubMembers/{club}. */
    public static List<String> eligibleIndexPaths(Session s) {
        List<String> paths = new ArrayList<>();
        if (s.isClub()) {
            paths.add("clubMembers/" + s.clubId);
        } else {
            for (String g : s.groupIds()) paths.add("studentsByGroup/" + g);
        }
        return paths;
    }

    /** Reads, once and fresh, the uids of every student who may attend the session. */
    public static void eligibleStudents(Session s, Callback<Set<String>> callback) {
        readEligible(eligibleIndexPaths(s), 0, new LinkedHashSet<>(), callback);
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
                for (DataSnapshot child : snapshot.getChildren()) into.add(child.getKey());
                readEligible(paths, i + 1, into, callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private void write(Session s, Set<String> students, String title, String body,
                       Callback<String> callback) {
        // push() makes a unique, time-ordered key on the phone; nothing is sent yet.
        String id = Db.root().child("sessions").push().getKey();
        if (id == null) {
            callback.onError(str(R.string.error_generic));
            return;
        }

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

        Map<String, Object> updates = new HashMap<>();
        updates.put("sessions/" + id, session);
        updates.put("sessionsByLecturer/" + s.lecturerUid + "/" + id, true);
        if (s.isClub()) {
            updates.put("sessionsByClub/" + s.clubId + "/" + id, true);
        } else {
            for (String g : s.groupIds()) updates.put("sessionsByGroup/" + g + "/" + id, true);
        }
        for (String studentUid : students) {
            String pushId = Db.root().child("notifications").child(studentUid).push().getKey();
            Map<String, Object> n = new HashMap<>();
            n.put("title", title);
            n.put("body", body);
            n.put("sessionId", id);
            n.put("time", ServerValue.TIMESTAMP);
            n.put("read", false);
            updates.put("notifications/" + studentUid + "/" + pushId, n);
        }

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
}
