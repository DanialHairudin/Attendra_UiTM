package com.attendra.uitm.data;


import com.attendra.uitm.model.Registration;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.Query;
import com.google.firebase.database.ServerValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reads and writes a user's account data. */
public class UserJava {

    /** "lecturers" or "students": the profile node for a role. */
    public static String profileNode(String role) {
        if (Registration.ROLE_LECTURER.equals(role)) {
            return "lecturers";
        }
        return "students";
    }

    /** lecturers/{uid} or students/{uid}, for a live listener. */
    public static DatabaseReference profileRef(String uid, String role) {
        return Db.root().child(profileNode(role)).child(uid);
    }

    /** Where a user's clubs are listed. */
    public static Query clubsQuery(String uid, String role) {
        if (Registration.ROLE_LECTURER.equals(role)) {
            return Db.root().child("clubAdvisors");
        }
        return Db.root().child("studentClubs").child(uid);
    }

    // ============================================================
    // [UPDATE] Join or leave a club
    // ============================================================
    /**
     * Joins or leaves a club (student), or starts or stops advising it (lecturer), in ONE
     * updateChildren so the indexes stay in sync.
     */
    public void setClub(String uid, String role, String clubId, boolean on,
                        Callback<Void> callback) {
        Object value;
        if (on) {
            value = Boolean.TRUE;
        } else {
            value = null;
        }
        Map<String, Object> updates = new HashMap<>();
        if (Registration.ROLE_LECTURER.equals(role)) {
            updates.put("clubAdvisors/" + clubId + "/" + uid, value);
        } else {
            updates.put("studentClubs/" + uid + "/" + clubId, value);
            updates.put("clubMembers/" + clubId + "/" + uid, value);
        }
        Db.write(updates, callback);
    }

    // ============================================================
    // [UPDATE] Save the user's phone number
    // ============================================================
    /** Saves {lecturers|students}/{uid}/phone, e.g. "+60123456789". */
    public void setPhone(String uid, String role, String phone, Callback<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(profileNode(role) + "/" + uid + "/phone", phone);
        Db.write(updates, callback);
    }

    /** How long after sign-up a student may change their track (once). Matches the rules. */
    public static final long TRACK_CHANGE_WINDOW_MS = 7L * 24 * 60 * 60 * 1000;

    // ============================================================
    // [UPDATE] Save the student's track (elective)
    // ============================================================
    /**
     * Student only: the track they picked, e.g. "BIG_DATA" (when their group doesn't set one).
     * The first pick is free; replacing a track also stamps trackChangedAt, which locks it for good.
     */
    public void setTrack(String uid, String track, boolean isChange, Callback<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("students/" + uid + "/track", track);
        if (isChange) {
            updates.put("students/" + uid + "/trackChangedAt", ServerValue.TIMESTAMP);
        }
        Db.write(updates, callback);
    }

    /** Club IDs (sorted) from a clubsQuery() snapshot. */
    public static List<String> clubIds(DataSnapshot snapshot, String uid, String role) {
        List<String> ids = new ArrayList<>();
        for (DataSnapshot club : snapshot.getChildren()) {
            boolean mine;
            if (Registration.ROLE_LECTURER.equals(role)) {
                mine = Boolean.TRUE.equals(club.child(uid).getValue(Boolean.class));
            } else {
                mine = Boolean.TRUE.equals(club.getValue(Boolean.class));
            }
            if (mine) {
                ids.add(club.getKey());
            }
        }
        Collections.sort(ids);
        return ids;
    }

    // ============================================================
    // [CREATE] Save a new user's profile into the database
    // ============================================================
    /**
     * Writes the new user's role, profile and every index node in ONE multi-path updateChildren()
     * call.
     */
    public void createProfile(String uid, Registration r, Callback<Void> callback) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("name", r.name);
        profile.put("email", r.email);
        profile.put("phone", r.phone);
        profile.put("emailVerified", false);
        profile.put("createdAt", ServerValue.TIMESTAMP);

        Map<String, Object> updates = new HashMap<>();
        if (r.isStudent()) {
            profile.put("studentId", r.studentId);
            profile.put("programme", r.programme);
            profile.put("part", r.part);
            profile.put("groupId", r.groupId);
            if (r.track != null) {
                profile.put("track", r.track);
            }
            // Claims the student ID; the rules allow each ID to be claimed only once.
            updates.put("studentIds/" + r.studentId, uid);
            updates.put("studentsByGroup/" + r.groupId + "/" + uid, true);
            for (String clubId : r.clubIds) {
                updates.put("studentClubs/" + uid + "/" + clubId, true);
                updates.put("clubMembers/" + clubId + "/" + uid, true);
            }
        } else {
            profile.put("staffId", r.staffId);
            for (String clubId : r.clubIds) {
                updates.put("clubAdvisors/" + clubId + "/" + uid, true);
            }
        }
        updates.put("roles/" + uid, r.role);
        updates.put(profileNode(r.role) + "/" + uid, profile);

        Db.write(updates, callback);
    }

    // ============================================================
    // [READ] Check if a student ID is already used
    // ============================================================
    /** True if studentIds/{studentId} is already claimed by an account other than {@code uid}. */
    public void isStudentIdTaken(String studentId, String uid, Callback<Boolean> callback) {
        readString("studentIds/" + studentId, new Callback<String>() {
            @Override
            public void onSuccess(String owner) {
                callback.onSuccess(owner != null && !owner.equals(uid));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    // ============================================================
    // [READ] Get the user's role (student or lecturer)
    // ============================================================
    /** Reads roles/{uid}. */
    public void getRole(String uid, Callback<String> callback) {
        readString("roles/" + uid, callback);
    }

    // ============================================================
    // [READ] Get the user's name
    // ============================================================
    /** Reads {lecturers|students}/{uid}/name (used by the placeholder home screens). */
    public void getName(String uid, String role, Callback<String> callback) {
        readString(profileNode(role) + "/" + uid + "/name", callback);
    }

    /** What login needs to know about a profile to decide where to go. */
    public static final class AccountState {
        public boolean emailVerified;
        /** Test accounts skip verification and the Preferred Name Page. */
        public boolean isTest;
        public boolean hasPreferredName;
    }

    // ============================================================
    // [READ] Get the user's profile after login
    // ============================================================
    /** Reads {lecturers|students}/{uid} once. */
    public void loadAccountState(String uid, String role, Callback<AccountState> callback) {
        Db.readOnce(Db.root().child(profileNode(role)).child(uid),
                new Callback<DataSnapshot>() {
                    @Override
                    public void onSuccess(DataSnapshot profile) {
                        AccountState state = new AccountState();
                        state.emailVerified = Boolean.TRUE.equals(
                                profile.child("emailVerified").getValue(Boolean.class));
                        state.isTest = Boolean.TRUE.equals(
                                profile.child("isTest").getValue(Boolean.class));
                        String preferred = profile.child("preferredName").getValue(String.class);
                        state.hasPreferredName = preferred != null && !preferred.trim().isEmpty();
                        callback.onSuccess(state);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
    }

    /** Reads one text value. */
    private void readString(String path, Callback<String> callback) {
        Db.readOnce(Db.root().child(path), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                callback.onSuccess(snapshot.getValue(String.class));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
}
