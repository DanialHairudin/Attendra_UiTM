package com.attendra.uitm.data;

import static com.attendra.uitm.AttendraApp.str;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.attendra.uitm.BuildConfig;
import com.attendra.uitm.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.Map;

/**
 * Single place that opens the Realtime Database. The URL is passed explicitly
 * (BuildConfig.DATABASE_URL) so the app does not depend on google-services.json
 * containing it.
 */
public final class Db {

    /** How long a one-time read may take before we report an error. */
    private static final long READ_TIMEOUT_MS = 15_000;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Db() {
    }

    public static FirebaseDatabase get() {
        return FirebaseDatabase.getInstance(BuildConfig.DATABASE_URL);
    }

    public static DatabaseReference root() {
        return get().getReference();
    }

    /**
     * Reads a path once, with a timeout.
     *
     * The Realtime Database SDK never says "can't connect" (offline, or a wrong
     * database URL). It just waits forever, which would leave a screen stuck on
     * "Loading…". So after READ_TIMEOUT_MS we stop listening and call onError.
     * The `done` flag makes sure the callback runs only once, whichever comes first.
     */
    public static void readOnce(Query query, Callback<DataSnapshot> callback) {
        final boolean[] done = {false};
        final ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (done[0]) return;
                done[0] = true;
                callback.onSuccess(snapshot);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (done[0]) return;
                done[0] = true;
                callback.onError(AuthErrors.database(error));
            }
        };
        MAIN.postDelayed(() -> {
            if (done[0]) return;
            done[0] = true;
            query.removeEventListener(listener);
            callback.onError(str(R.string.error_network));
        }, READ_TIMEOUT_MS);
        query.addListenerForSingleValueEvent(listener);
    }

    /**
     * Multi-path write (updateChildren) with the same timeout as readOnce.
     *
     * Offline, Firebase keeps a write queued and only finishes it once the server
     * answers. On timeout we call purgeOutstandingWrites() so the queued write is
     * thrown away and can't sneak in later, after we've told the user it failed.
     */
    public static void write(Map<String, Object> updates, Callback<Void> callback) {
        final boolean[] done = {false};
        MAIN.postDelayed(() -> {
            if (done[0]) return;
            done[0] = true;
            get().purgeOutstandingWrites();
            callback.onError(str(R.string.error_network));
        }, READ_TIMEOUT_MS);
        root().updateChildren(updates).addOnCompleteListener(task -> {
            if (done[0]) return;
            done[0] = true;
            if (task.isSuccessful()) {
                callback.onSuccess(null);
            } else {
                Exception e = task.getException();
                callback.onError(e != null ? AuthErrors.message(e) : str(R.string.error_generic));
            }
        });
    }
}
