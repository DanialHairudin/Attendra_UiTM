package com.attendra.uitm.ui.auth;

import static com.attendra.uitm.AttendraApp.str;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.attendra.uitm.R;
import com.attendra.uitm.data.AuthJava;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.VerificationJava;
import com.attendra.uitm.data.VerificationJava.Result;
import com.google.firebase.auth.FirebaseUser;

import java.util.HashMap;
import java.util.Map;

/** State of the Email Verification Page. */
public class VerificationViewModel extends AndroidViewModel {

    /** Everything the screen draws. */
    public static class State {
        /** Reading the role / waiting code when the screen opens. */
        public boolean loading = true;
        public boolean sending;
        public boolean verifying;
        /** When the current code stops working. */
        public long expiresAt;
        /** When Resend becomes available again. */
        public long resendAt;
        /** 5 wrong tries: a new code is needed. */
        public boolean locked;
        /** The last send failed (shows the "could not send" message). */
        public boolean sendFailed;
        /** Other message (incorrect code, network error). */
        public String error;
        /** The screen could not start (signed out, no role, offline). */
        public boolean fatal;
    }

    private final AuthJava auth = new AuthJava();
    private final VerificationJava codes;

    private final State state = new State();
    private final MutableLiveData<State> stateLive = new MutableLiveData<>(state);
    /** Set once when the code is right: the role whose dashboard to open. */
    private final MutableLiveData<String> verifiedRole = new MutableLiveData<>();
    /** One-shot events for the screen. */
    private final MutableLiveData<Boolean> clearBoxes = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> codeSent = new MutableLiveData<>(false);

    /** More changes to save when the code is right (moving to another phone). */
    private Map<String, Object> extraUpdates = new HashMap<>();

    private boolean started;
    private String uid;
    private String role;

    public VerificationViewModel(@NonNull Application app) {
        super(app);
        codes = new VerificationJava(app);
    }

    /** Everything the page shows. */
    public LiveData<State> getState() {
        return stateLive;
    }

    /** The role once the code is right. */
    public LiveData<String> getVerifiedRole() {
        return verifiedRole;
    }

    /** True when the boxes must be emptied. */
    public LiveData<Boolean> getClearBoxes() {
        return clearBoxes;
    }

    /** True when a new code was sent. */
    public LiveData<Boolean> getCodeSent() {
        return codeSent;
    }

    /** The page emptied the boxes. */
    public void clearBoxesHandled() {
        clearBoxes.setValue(false);
    }

    /** The page showed "code sent". */
    public void codeSentHandled() {
        codeSent.setValue(false);
    }

    /** Changes to save together with the code check. */
    public void setExtraUpdates(Map<String, Object> updates) {
        extraUpdates = updates;
    }

    /** Called when the screen opens. */
    public void start(boolean sendNow) {
        if (started) return;
        started = true;

        FirebaseUser user = auth.currentUser();
        if (user == null) {
            fail(str(R.string.error_session_ended));
            return;
        }
        uid = user.getUid();
        auth.loadRole(user, new Callback<String>() {
            @Override
            public void onSuccess(String r) {
                role = r;
                if (sendNow) {
                    state.loading = false;
                    resend();
                } else {
                    loadPending();
                }
            }

            @Override
            public void onError(String message) {
                fail(message);
            }
        });
    }

    /** Reads the waiting code, or sends a new one. */
    private void loadPending() {
        codes.loadPending(uid, new Callback<VerificationJava.Pending>() {
            @Override
            public void onSuccess(VerificationJava.Pending p) {
                state.loading = false;
                // No code yet, or the old one ran out (e.g. coming back from Log in later):
                // send a fresh one instead of showing "expired" straight away.
                if (p == null || System.currentTimeMillis() >= p.expiresAt) {
                    resend();
                    return;
                }
                state.expiresAt = p.expiresAt;
                state.resendAt = p.sentAt + VerificationJava.RESEND_COOLDOWN_MS;
                state.locked = p.attempts >= VerificationJava.MAX_ATTEMPTS;
                publish();
            }

            @Override
            public void onError(String message) {
                fail(message);
            }
        });
    }

    /** Creates a NEW code and emails it. */
    public void resend() {
        if (role == null || state.sending) return;
        state.sending = true;
        state.sendFailed = false;
        state.error = null;
        publish();
        codes.sendNewCode(uid, role, new Callback<Long>() {
            @Override
            public void onSuccess(Long expiresAt) {
                state.sending = false;
                state.expiresAt = expiresAt;
                state.resendAt = System.currentTimeMillis()
                        + VerificationJava.RESEND_COOLDOWN_MS;
                state.locked = false;
                clearBoxes.setValue(true);
                codeSent.setValue(true);
                publish();
            }

            @Override
            public void onError(String message) {
                state.sending = false;
                // The database may already hold a new code that never arrived, so treat the old one
                // as unusable.
                state.expiresAt = 0;
                state.resendAt = 0;
                state.sendFailed = true;
                publish();
            }
        });
    }

    /** Checks the typed code. */
    public void verify(String code) {
        if (role == null || state.verifying) return;
        state.verifying = true;
        state.error = null;
        publish();
        codes.verify(uid, role, code, extraUpdates, new Callback<Result>() {
            @Override
            public void onSuccess(Result result) {
                state.verifying = false;
                switch (result) {
                    case OK:
                        verifiedRole.setValue(role);
                        return; // The screen closes
                    case WRONG:
                        state.error = str(R.string.code_incorrect);
                        clearBoxes.setValue(true);
                        break;
                    case TOO_MANY:
                        state.locked = true;
                        clearBoxes.setValue(true);
                        break;
                    case EXPIRED:
                        state.expiresAt = 0;
                        break;
                }
                publish();
            }

            @Override
            public void onError(String message) {
                state.verifying = false;
                state.error = message;
                publish();
            }
        });
    }

    /** Typing a new digit hides "The code is incorrect". */
    public void clearError() {
        if (state.error != null && !state.fatal) {
            state.error = null;
            publish();
        }
    }

    /** Shows an error that stops this page. */
    private void fail(String message) {
        state.loading = false;
        state.fatal = true;
        state.error = message;
        publish();
    }

    /** Tells the page to draw again. */
    private void publish() {
        stateLive.setValue(state);
    }
}
