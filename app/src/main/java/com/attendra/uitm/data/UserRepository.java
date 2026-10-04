package com.attendra.uitm.data;


import com.attendra.uitm.model.Registration;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.ServerValue;

import java.util.HashMap;
import java.util.Map;

/** Reads and writes /users/{uid} and the index nodes that hang off a user. */
public class UserRepository {

    /**
     * Writes the new user's profile and every index node in ONE multi-path
     * updateChildren() call. Either all paths are written or none are, so the
     * indexes can never get out of sync with the profile (CLAUDE.md section 7).
     *
     * Student writes:
     *   users/{uid}, studentsByGroup/{groupId}/{uid},
     *   studentClubs/{uid}/{clubId}, clubMembers/{clubId}/{uid}
     * Lecturer writes:
     *   users/{uid}, clubAdvisors/{clubId}/{uid}
     */
    public void createProfile(String uid, Registration r, Callback<Void> callback) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("role", r.role);
        profile.put("name", r.name);
        profile.put("email", r.email);
        profile.put("phone", r.phone);
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
        updates.put("users/" + uid, profile);

        Db.write(updates, callback);
    }

    /** Reads users/{uid}/role. Returns null in onSuccess if the user has no profile. */
    public void getRole(String uid, Callback<String> callback) {
        readString("users/" + uid + "/role", callback);
    }

    /** Reads users/{uid}/name (used by the placeholder home screens). */
    public void getName(String uid, Callback<String> callback) {
        readString("users/" + uid + "/name", callback);
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
