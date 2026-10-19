package com.attendra.uitm.data;

import static com.attendra.uitm.AttendraApp.str;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.attendra.uitm.BuildConfig;
import com.attendra.uitm.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.Map;

/** Single place that opens the Realtime Database. */
public final class Db {

    /** How long a one-time read may take before we report an error. */
    private static final long READ_TIMEOUT_MS = 15_000;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Db() {
    }

    /** Debug + -PuseEmulators=true only: the one instance pointed at the local emulator. */
    private static FirebaseDatabase emulator;

    public static synchronized FirebaseDatabase get() {
        if (!BuildConfig.USE_EMULATORS) return FirebaseDatabase.getInstance(BuildConfig.DATABASE_URL);
        // Kept, because getInstance() after useEmulator() would hand back a new live instance.
        if (emulator == null) {
            emulator = FirebaseDatabase.getInstance(BuildConfig.DATABASE_URL);
            emulator.useEmulator(BuildConfig.EMULATOR_HOST, 9000);
        }
        return emulator;
    }

    public static DatabaseReference root() {
        return get().getReference();
    }

    /** A value as text, or null if it is missing. */
    @Nullable
    public static String text(DataSnapshot snapshot) {
        Object value = snapshot.getValue();
        return value == null ? null : String.valueOf(value);
    }

    /** Reads a path once, with a timeout. */
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

    /** Multi-path write (updateChildren) with the same timeout as readOnce. */
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
