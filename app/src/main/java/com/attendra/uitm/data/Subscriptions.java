package com.attendra.uitm.data;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps track of live database listeners (ValueEventListeners) so a screen can
 * remove all of them at once.
 *
 * A ValueEventListener stays attached until it is removed, and keeps calling
 * back (and using data) even after the screen is gone. So screens add their
 * listeners in onStart() and call clear() in onStop() (CLAUDE.md 12.5).
 */
public class Subscriptions {

    /** One attached listener. remove() detaches just this one. */
    public final class Handle {
        private final Query query;
        private final ValueEventListener listener;

        private Handle(Query query, ValueEventListener listener) {
            this.query = query;
            this.listener = listener;
        }

        public void remove() {
            query.removeEventListener(listener);
            handles.remove(this);
        }
    }

    private final List<Handle> handles = new ArrayList<>();

    /**
     * Calls onSuccess with the current value straight away, then again every
     * time the data changes. onError gets a short message (e.g. permission denied).
     */
    public Handle listen(Query query, Callback<DataSnapshot> callback) {
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                callback.onSuccess(snapshot);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(AuthErrors.database(error));
            }
        };
        query.addValueEventListener(listener);
        Handle handle = new Handle(query, listener);
        handles.add(handle);
        return handle;
    }

    /** Removes every listener added through this object. */
    public void clear() {
        for (Handle h : new ArrayList<>(handles)) h.remove();
    }
}
