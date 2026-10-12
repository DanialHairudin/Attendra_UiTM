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

/**
 * Reads and writes a user's account data (CLAUDE.md 12.1):
 *
 *   roles/{uid}        "lecturer" | "student"   small lookup for routing and rules
 *   lecturers/{uid}    lecturer profile
 *   students/{uid}     student profile
 *
 * plus the index nodes that hang off a user (studentsByGroup, studentClubs,
 * clubMembers, clubAdvisors).
 */
public class UserRepository {

    /** "lecturers" or "students": the profile node for a role. */
    public static String profileNode(String role) {
        return Registration.ROLE_LECTURER.equals(role) ? "lecturers" : "students";
    }

    /** lecturers/{uid} or students/{uid}, for a live listener. */
    public static DatabaseReference profileRef(String uid, String role) {
        return Db.root().child(profileNode(role)).child(uid);
    }

    /**
     * Where a user's clubs are listed:
     * - student: studentClubs/{uid} = {clubId: true}
     * - lecturer: there is no per-lecturer index, so the whole clubAdvisors node
     *   ({clubId: {uid: true}}) is read. It is small (one entry per club).
     * Use clubIds() to turn the snapshot into a list.
     */
    public static Query clubsQuery(String uid, String role) {
        return Registration.ROLE_LECTURER.equals(role)
                ? Db.root().child("clubAdvisors")
                : Db.root().child("studentClubs").child(uid);
    }

    /**
     * Joins or leaves a club (student), or starts or stops advising it (lecturer),
     * in ONE updateChildren so the indexes stay in sync (CLAUDE.md C4):
     * - student:  studentClubs/{uid}/{clubId} and clubMembers/{clubId}/{uid}
     * - lecturer: clubAdvisors/{clubId}/{uid}
     * null removes the entry.
     */
    public void setClub(String uid, String role, String clubId, boolean on,
                        Callback<Void> callback) {
        Object value = on ? Boolean.TRUE : null;
        Map<String, Object> updates = new HashMap<>();
        if (Registration.ROLE_LECTURER.equals(role)) {
            updates.put("clubAdvisors/" + clubId + "/" + uid, value);
        } else {
            updates.put("studentClubs/" + uid + "/" + clubId, value);
            updates.put("clubMembers/" + clubId + "/" + uid, value);
        }
        Db.write(updates, callback);
    }

    /** Saves {lecturers|students}/{uid}/phone, e.g. "+60123456789". */
    public void setPhone(String uid, String role, String phone, Callback<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(profileNode(role) + "/" + uid + "/phone", phone);
        Db.write(updates, callback);
    }

    /** Club IDs (sorted) from a clubsQuery() snapshot. */
    public static List<String> clubIds(DataSnapshot snapshot, String uid, String role) {
        List<String> ids = new ArrayList<>();
        for (DataSnapshot club : snapshot.getChildren()) {
            boolean mine = Registration.ROLE_LECTURER.equals(role)
                    ? Boolean.TRUE.equals(club.child(uid).getValue(Boolean.class))
                    : Boolean.TRUE.equals(club.getValue(Boolean.class));
            if (mine) ids.add(club.getKey());
        }
        Collections.sort(ids);
        return ids;
    }

    /**
     * Writes the new user's role, profile and every index node in ONE multi-path
     * updateChildren() call. Either all paths are written or none are, so the
     * indexes can never get out of sync with the profile.
     *
     * Student writes:
     *   roles/{uid}, students/{uid}, studentsByGroup/{groupId}/{uid},
     *   studentClubs/{uid}/{clubId}, clubMembers/{clubId}/{uid}
     * Lecturer writes:
     *   roles/{uid}, lecturers/{uid}, clubAdvisors/{clubId}/{uid}
     *
     * emailVerified starts false. It becomes true once the user enters the
     * 6-digit code from the email (VerificationRepository).
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

    /** Reads roles/{uid}. Returns null in onSuccess if the user has no role yet. */
    public void getRole(String uid, Callback<String> callback) {
        readString("roles/" + uid, callback);
    }

    /** Reads {lecturers|students}/{uid}/name (used by the placeholder home screens). */
    public void getName(String uid, String role, Callback<String> callback) {
        readString(profileNode(role) + "/" + uid + "/name", callback);
    }

    /** What login needs to know about a profile to decide where to go. */
    public static final class AccountState {
        public boolean emailVerified;
        /** Test accounts skip verification and the Preferred Name Page (CLAUDE.md 12.4, 12.6). */
        public boolean isTest;
        public boolean hasPreferredName;
    }

    /** Reads {lecturers|students}/{uid} once. Missing values count as false. */
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
