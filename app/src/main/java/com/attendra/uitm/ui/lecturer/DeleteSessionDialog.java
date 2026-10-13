package com.attendra.uitm.ui.lecturer;

import android.content.Context;

import com.attendra.uitm.R;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.SessionRepository;
import com.attendra.uitm.model.Session;
import com.attendra.uitm.ui.common.SessionText;
import com.attendra.uitm.util.Formats;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * "Delete Test 1 · ICT600 (13 Oct)?" confirm dialog, shared by the Session Report Page and the
 * Session History tab.
 */
final class DeleteSessionDialog {

    interface Listener {
        /** "Share CSV first": export the report before deciding. */
        void onShareCsvFirst();

        void onDeleted();

        void onError(String message);
    }

    private DeleteSessionDialog() {
    }

    static void show(Context c, Session s, Listener listener) {
        String label = SessionText.join(s.title, SessionText.course(s))
                + " (" + Formats.dayOfMonth(s.start) + " " + Formats.shortMonth(s.start) + ")";
        new MaterialAlertDialogBuilder(c)
                .setMessage(c.getString(R.string.delete_session_body, label))
                .setPositiveButton(R.string.delete_session_confirm, (d, w) ->
                        new SessionRepository().delete(s, new Callback<Void>() {
                            @Override
                            public void onSuccess(Void unused) {
                                listener.onDeleted();
                            }

                            @Override
                            public void onError(String message) {
                                listener.onError(message);
                            }
                        }))
                .setNeutralButton(R.string.delete_share_first, (d, w) -> listener.onShareCsvFirst())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
