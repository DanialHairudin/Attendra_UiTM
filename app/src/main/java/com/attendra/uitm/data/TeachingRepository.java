package com.attendra.uitm.data;

import androidx.annotation.Nullable;

import com.attendra.uitm.model.TeachingAssignment;
import com.attendra.uitm.util.SemesterUtil;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ServerValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A lecturer's teaching classes in teachingAssignments/{uid}. */
public final class TeachingRepository {

    private TeachingRepository() {
    }

    public static DatabaseReference ref(String uid) {
        return Db.root().child("teachingAssignments").child(uid);
    }

    /** Reads the children of teachingAssignments/{uid}, sorted by course then group. */
    public static List<TeachingAssignment> parse(DataSnapshot snapshot) {
        List<TeachingAssignment> list = new ArrayList<>();
        for (DataSnapshot a : snapshot.getChildren()) {
            TeachingAssignment item = new TeachingAssignment(
                    a.child("courseId").getValue(String.class),
                    a.child("groupId").getValue(String.class),
                    a.child("semester").getValue(String.class));
            item.createdAt = a.child("createdAt").getValue(Long.class);
            if (item.courseId == null || item.groupId == null) continue;
            list.add(item);
        }
        Collections.sort(list, (x, y) -> {
            int byCourse = x.courseId.compareTo(y.courseId);
            return byCourse != 0 ? byCourse : x.groupId.compareTo(y.groupId);
        });
        return list;
    }

    /** Only the classes of the current semester (old ones without a semester count too). */
    public static List<TeachingAssignment> current(List<TeachingAssignment> all) {
        List<TeachingAssignment> list = new ArrayList<>();
        for (TeachingAssignment a : all) {
            if (SemesterUtil.isCurrent(a.semester)) list.add(a);
        }
        return list;
    }

    /**
     * Saves one class per group, and removes {@code oldKey} in the same update (an edit; null when
     * adding).
     */
    public static void save(String uid, String semester, String courseId,
                            Collection<String> groupIds, @Nullable String oldKey,
                            @Nullable Long keepCreatedAt, Callback<Void> callback) {
        String base = "teachingAssignments/" + uid + "/";
        Map<String, Object> updates = new HashMap<>();
        if (oldKey != null) updates.put(base + oldKey, null);
        for (String groupId : groupIds) {
            String key = TeachingAssignment.key(courseId, groupId);
            Map<String, Object> value = new HashMap<>();
            value.put("courseId", courseId);
            value.put("groupId", groupId);
            value.put("semester", semester);
            // The rules allow createdAt = now only for a new key; an unchanged key must keep its
            // first value.
            boolean sameKey = key.equals(oldKey) && keepCreatedAt != null;
            value.put("createdAt", sameKey ? keepCreatedAt : ServerValue.TIMESTAMP);
            updates.put(base + key, value);
        }
        Db.write(updates, callback);
    }

    public static void remove(String uid, String key, Callback<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("teachingAssignments/" + uid + "/" + key, null);
        Db.write(updates, callback);
    }

    /** True if the lecturer has at least one class this semester. */
    public static void hasCurrent(String uid, Callback<Boolean> callback) {
        Db.readOnce(ref(uid), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                callback.onSuccess(!current(parse(snapshot)).isEmpty());
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
}
