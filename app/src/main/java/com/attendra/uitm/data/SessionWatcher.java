package com.attendra.uitm.data;

import com.attendra.uitm.model.Session;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.Query;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Keeps a live list of sessions found through one or more index nodes, e.g.
 * sessionsByLecturer/{uid} (lecturer dashboard) sessionsByGroup/{groupId} (student dashboard)
 * sessionsByClub/{clubId} (student dashboard, one per club)
 */
public class SessionWatcher {

    public interface Listener {
        void onSessions(Map<String, Session> sessions, boolean ready);

        void onError(String message);
    }

    private final Subscriptions subs = new Subscriptions();
    private final Listener listener;

    /** Index name → listener on that index. */
    private final Map<String, Subscriptions.Handle> indexHandles = new HashMap<>();
    /** Index name → session IDs it lists. */
    private final Map<String, Set<String>> idsByIndex = new HashMap<>();
    /** Session ID → listener on sessions/{id}. */
    private final Map<String, Subscriptions.Handle> sessionHandles = new HashMap<>();
    private final Map<String, Session> sessions = new LinkedHashMap<>();
    /** Session IDs whose node has arrived at least once. */
    private final Set<String> loaded = new HashSet<>();

    public SessionWatcher(Listener listener) {
        this.listener = listener;
    }

    /** Starts following an index node. */
    public void watchIndex(String name, Query index) {
        if (indexHandles.containsKey(name)) return;
        idsByIndex.put(name, null);
        indexHandles.put(name, subs.listen(index, new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                Set<String> ids = new HashSet<>();
                for (DataSnapshot child : snapshot.getChildren()) ids.add(child.getKey());
                idsByIndex.put(name, ids);
                sync();
            }

            @Override
            public void onError(String message) {
                idsByIndex.put(name, new HashSet<>());
                listener.onError(message);
                sync();
            }
        }));
        sync();
    }

    /** Stops following an index node (e.g. the student left a club). */
    public void unwatchIndex(String name) {
        Subscriptions.Handle h = indexHandles.remove(name);
        if (h != null) h.remove();
        idsByIndex.remove(name);
        sync();
    }

    /** Names of the indexes being followed. */
    public Set<String> indexNames() {
        return new HashSet<>(indexHandles.keySet());
    }

    /** Removes every listener. */
    public void stop() {
        subs.clear();
        indexHandles.clear();
        idsByIndex.clear();
        sessionHandles.clear();
        sessions.clear();
        loaded.clear();
    }

    /** Adds listeners for new IDs, removes them for IDs no index lists any more. */
    private void sync() {
        Set<String> wanted = new HashSet<>();
        for (Set<String> ids : idsByIndex.values()) {
            if (ids != null) wanted.addAll(ids);
        }
        for (String id : new HashSet<>(sessionHandles.keySet())) {
            if (!wanted.contains(id)) {
                sessionHandles.remove(id).remove();
                sessions.remove(id);
                loaded.remove(id);
            }
        }
        for (String id : wanted) {
            if (!sessionHandles.containsKey(id)) listenToSession(id);
        }
        publish();
    }

    private void listenToSession(String id) {
        sessionHandles.put(id, subs.listen(Db.root().child("sessions").child(id),
                new Callback<DataSnapshot>() {
                    @Override
                    public void onSuccess(DataSnapshot snapshot) {
                        Session s = snapshot.getValue(Session.class);
                        if (s != null) {
                            s.id = id;
                            sessions.put(id, s);
                        } else {
                            sessions.remove(id); // Index points at a deleted session
                        }
                        loaded.add(id);
                        publish();
                    }

                    @Override
                    public void onError(String message) {
                        loaded.add(id);
                        listener.onError(message);
                        publish();
                    }
                }));
    }

    private void publish() {
        boolean ready = !idsByIndex.containsValue(null)
                && loaded.containsAll(sessionHandles.keySet());
        listener.onSessions(new LinkedHashMap<>(sessions), ready);
    }
}
