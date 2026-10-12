package com.attendra.uitm.data;

import androidx.annotation.Nullable;

import com.attendra.uitm.model.Attendance;
import com.attendra.uitm.model.AttendanceRecord;
import com.attendra.uitm.model.Attempt;
import com.attendra.uitm.model.Session;
import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the Live Monitor and the Session Report show about ONE session,
 * kept up to date with ValueEventListeners:
 *
 *   sessions/{sid}                         the session (end time, radius, status)
 *   studentsByGroup/{g} or clubMembers/{c} who may attend ("eligible")
 *   attendance/{sid}                       who checked in / was marked
 *   attempts/{sid}                         every check-in attempt (rejected ones listed)
 *
 * Student names and IDs (students/{uid}) are read once each and kept, because
 * they don't change during a session. Call start() in onStart and stop() in onStop.
 */
public class SessionAttendanceWatcher {

    public interface Listener {
        /** Something changed; read the public fields and redraw. */
        void onChanged();

        void onError(String message);
    }

    /** Name and ID of one student. */
    public static final class Person {
        public final String name;
        public final String studentId;
        public final String groupId;

        Person(String name, String studentId, String groupId) {
            this.name = name == null ? "" : name;
            this.studentId = studentId == null ? "" : studentId;
            this.groupId = groupId == null ? "" : groupId;
        }
    }

    private final String sessionId;
    private final Listener listener;
    private final Subscriptions subs = new Subscriptions();
    private boolean indexesAttached;

    @Nullable
    public Session session;
    /** uid → record. null until attendance/{sid} has loaded once. */
    @Nullable
    public Map<String, AttendanceRecord> records;
    /** All attempts, oldest first. null until attempts/{sid} has loaded once. */
    @Nullable
    public List<Attempt> attempts;
    /**
     * The latest raw snapshots of attendance/{sid} and attempts/{sid}, for the
     * live check-in map (map/LiveCheckInMap.fromSnapshots). null until loaded.
     */
    @Nullable
    public DataSnapshot attendanceSnapshot;
    @Nullable
    public DataSnapshot attemptsSnapshot;
    /** Index path → its student uids; null value = that index is still loading. */
    private final Map<String, Set<String>> eligibleByIndex = new LinkedHashMap<>();
    public final Map<String, Person> people = new HashMap<>();
    private final Set<String> lookups = new HashSet<>();

    public SessionAttendanceWatcher(String sessionId, Listener listener) {
        this.sessionId = sessionId;
        this.listener = listener;
    }

    public void start() {
        subs.listen(Db.root().child("sessions").child(sessionId), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                session = snapshot.getValue(Session.class);
                if (session != null) {
                    session.id = sessionId;
                    attachIndexes(session);
                }
                listener.onChanged();
            }

            @Override
            public void onError(String message) {
                listener.onError(message);
            }
        });
        subs.listen(Db.root().child("attendance").child(sessionId), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                Map<String, AttendanceRecord> map = new LinkedHashMap<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    AttendanceRecord r = child.getValue(AttendanceRecord.class);
                    if (r != null) {
                        map.put(child.getKey(), r);
                        lookUp(child.getKey());
                    }
                }
                records = map;
                attendanceSnapshot = snapshot;
                listener.onChanged();
            }

            @Override
            public void onError(String message) {
                listener.onError(message);
            }
        });
        subs.listen(Db.root().child("attempts").child(sessionId), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                List<Attempt> list = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Attempt a = child.getValue(Attempt.class);
                    if (a != null) {
                        list.add(a);
                        lookUp(a.uid);
                    }
                }
                attempts = list;
                attemptsSnapshot = snapshot;
                listener.onChanged();
            }

            @Override
            public void onError(String message) {
                listener.onError(message);
            }
        });
    }

    public void stop() {
        subs.clear();
        indexesAttached = false;
        eligibleByIndex.clear();
    }

    /** The groups (or club) never change, so their indexes are attached once. */
    private void attachIndexes(Session s) {
        if (indexesAttached) return;
        indexesAttached = true;
        for (String path : SessionRepository.eligibleIndexPaths(s)) {
            eligibleByIndex.put(path, null);
            subs.listen(Db.root().child(path), new Callback<DataSnapshot>() {
                @Override
                public void onSuccess(DataSnapshot snapshot) {
                    // Only students in the session's world (real or test) are expected.
                    TestWorld.filter(TestWorld.keys(snapshot), TestWorld.of(s),
                            new Callback<Set<String>>() {
                                @Override
                                public void onSuccess(Set<String> uids) {
                                    if (!eligibleByIndex.containsKey(path)) return; // stopped
                                    for (String uid : uids) lookUp(uid);
                                    eligibleByIndex.put(path, uids);
                                    listener.onChanged();
                                }

                                @Override
                                public void onError(String message) {
                                    listener.onError(message);
                                }
                            });
                }

                @Override
                public void onError(String message) {
                    listener.onError(message);
                }
            });
        }
    }

    /** Reads students/{uid} once for a uid we have not seen yet. */
    private void lookUp(@Nullable String uid) {
        if (uid == null || !lookups.add(uid)) return;
        Db.readOnce(Db.root().child("students").child(uid), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot p) {
                people.put(uid, new Person(p.child("name").getValue(String.class),
                        Db.text(p.child("studentId")),
                        p.child("groupId").getValue(String.class)));
                listener.onChanged();
            }

            @Override
            public void onError(String message) {
                lookups.remove(uid); // try again on the next update
            }
        });
    }

    // --------------------------------------------------------------- results

    /** True once the session, its records, attempts and eligible lists have all loaded. */
    public boolean ready() {
        return session != null && records != null && attempts != null && indexesAttached
                && !eligibleByIndex.containsValue(null);
    }

    /** Everyone who may attend, plus anyone with a record (e.g. marked before leaving a group). */
    public Set<String> eligible() {
        Set<String> all = new LinkedHashSet<>();
        for (Set<String> uids : eligibleByIndex.values()) if (uids != null) all.addAll(uids);
        if (records != null) all.addAll(records.keySet());
        return all;
    }

    @Nullable
    public String statusOf(String uid) {
        AttendanceRecord r = records == null ? null : records.get(uid);
        return r == null ? null : r.status;
    }

    public int count(String status) {
        int n = 0;
        if (records != null) {
            for (AttendanceRecord r : records.values()) if (status.equals(r.status)) n++;
        }
        return n;
    }

    /** Eligible students with no record yet ("Not yet"). */
    public List<String> notYet() {
        List<String> list = new ArrayList<>();
        for (String uid : eligible()) if (statusOf(uid) == null) list.add(uid);
        return list;
    }

    /** Rejected attempts, newest first. */
    public List<Attempt> rejectedAttempts() {
        List<Attempt> list = new ArrayList<>();
        if (attempts != null) {
            for (Attempt a : attempts) if (a.isRejected()) list.add(0, a);
        }
        return list;
    }

    /**
     * The "Rejected" count: students whose attempts were rejected and who are
     * still not checked in (a later successful check-in no longer counts).
     */
    public int rejectedCount() {
        Set<String> uids = new HashSet<>();
        for (Attempt a : rejectedAttempts()) {
            if (a.uid != null && !Attendance.attended(statusOf(a.uid))) uids.add(a.uid);
        }
        return uids.size();
    }

    public Person person(String uid) {
        Person p = people.get(uid);
        return p != null ? p : new Person("", "", "");
    }
}
