package com.attendra.uitm.data;

import androidx.annotation.Nullable;

import com.attendra.uitm.model.Registration;
import com.attendra.uitm.model.Session;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps the two tester accounts (isTest: true) apart from real lecturers and students, so testing
 * never shows up in the real app.
 */
public final class TestWorld {

    /** Uid → isTest, for the signed-in user and every student looked up. */
    private static final Map<String, Boolean> flags = new HashMap<>();

    private TestWorld() {
    }

    /** Called at login, where the profile has just been read anyway. */
    public static void remember(String uid, boolean isTest) {
        flags.put(uid, isTest);
    }

    /** The cached value for a uid, or false if it has not been read yet. */
    public static boolean cached(@Nullable String uid) {
        return uid != null && Boolean.TRUE.equals(flags.get(uid));
    }

    /** True if the session was made by a test lecturer. */
    public static boolean of(@Nullable Session s) {
        return s != null && s.isTest;
    }

    /** Whether the signed-in user is a test account. */
    public static void mine(String role, Callback<Boolean> callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onSuccess(false);
            return;
        }
        read(user.getUid(), UserRepository.profileRef(user.getUid(), role), callback);
    }

    /** Keeps only the students in the given world (test or real), in the same order. */
    public static void filter(Collection<String> uids, boolean test,
                              Callback<Set<String>> callback) {
        List<String> todo = new ArrayList<>();
        for (String uid : uids) if (!flags.containsKey(uid)) todo.add(uid);
        if (todo.isEmpty()) {
            callback.onSuccess(pick(uids, test));
            return;
        }
        // Read the missing ones in parallel; answer once the last one is back.
        final int[] left = {todo.size()};
        final boolean[] failed = {false};
        for (String uid : todo) {
            read(uid, UserRepository.profileRef(uid, Registration.ROLE_STUDENT),
                    new Callback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean isTest) {
                            if (--left[0] == 0 && !failed[0]) {
                                callback.onSuccess(pick(uids, test));
                            }
                        }

                        @Override
                        public void onError(String message) {
                            if (failed[0]) return;
                            failed[0] = true;
                            callback.onError(message);
                        }
                    });
        }
    }

    /** filter() for a lecturer screen: keeps the students in the lecturer's own world. */
    public static void forLecturer(Collection<String> uids, Callback<Set<String>> callback) {
        mine(Registration.ROLE_LECTURER, new Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean test) {
                filter(uids, test, callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /** The uids (children) of an index snapshot such as studentsByGroup/{group}. */
    public static List<String> keys(DataSnapshot snapshot) {
        List<String> uids = new ArrayList<>();
        for (DataSnapshot child : snapshot.getChildren()) uids.add(child.getKey());
        return uids;
    }

    private static Set<String> pick(Collection<String> uids, boolean test) {
        Set<String> out = new LinkedHashSet<>();
        for (String uid : uids) if (cached(uid) == test) out.add(uid);
        return out;
    }

    private static void read(String uid, DatabaseReference profile, Callback<Boolean> callback) {
        Boolean known = flags.get(uid);
        if (known != null) {
            callback.onSuccess(known);
            return;
        }
        Db.readOnce(profile.child("isTest"), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                boolean isTest = Boolean.TRUE.equals(snapshot.getValue(Boolean.class));
                flags.put(uid, isTest);
                callback.onSuccess(isTest);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
}
