package com.attendra.uitm.data;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * hiddenSessions/{uid}/{sid} = true: closed sessions a student has hidden from their own lists (My
 * Attendance history, Notifications).
 */
public final class HiddenSessions {

    private HiddenSessions() {
    }

    public static DatabaseReference ref(String uid) {
        return Db.root().child("hiddenSessions").child(uid);
    }

    /** The hidden session IDs in a snapshot of hiddenSessions/{uid}. */
    public static Set<String> ids(DataSnapshot snapshot) {
        Set<String> ids = new HashSet<>();
        for (DataSnapshot child : snapshot.getChildren()) {
            if (Boolean.TRUE.equals(child.getValue(Boolean.class))) ids.add(child.getKey());
        }
        return ids;
    }

    /** Hides a closed session. */
    public static void hide(String uid, String sessionId, Callback<Void> callback) {
        set(uid, sessionId, true, callback);
    }

    public static void unhide(String uid, String sessionId, Callback<Void> callback) {
        set(uid, sessionId, null, callback);
    }

    private static void set(String uid, String sessionId, Object value, Callback<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("hiddenSessions/" + uid + "/" + sessionId, value);
        Db.write(updates, callback);
    }
}
