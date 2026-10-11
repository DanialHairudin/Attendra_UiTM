package com.attendra.uitm.data;


import com.attendra.uitm.model.Registration;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.ServerValue;

import java.util.HashMap;
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
     * emailVerified starts false. It becomes true once the user proves they own
     * the email address.
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

    /**
     * Reads {lecturers|students}/{uid}/emailVerified. A missing value counts as
     * false. The test accounts have it set to true in the database.
     */
    public void isEmailVerified(String uid, String role, Callback<Boolean> callback) {
        Db.readOnce(Db.root().child(profileNode(role)).child(uid).child("emailVerified"),
                new Callback<DataSnapshot>() {
                    @Override
                    public void onSuccess(DataSnapshot snapshot) {
                        callback.onSuccess(Boolean.TRUE.equals(snapshot.getValue(Boolean.class)));
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
