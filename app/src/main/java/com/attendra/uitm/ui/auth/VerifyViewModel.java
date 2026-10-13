package com.attendra.uitm.ui.auth;

import static com.attendra.uitm.AttendraApp.str;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.attendra.uitm.R;
import com.attendra.uitm.data.AuthRepository;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.VerificationRepository;
import com.attendra.uitm.data.VerificationRepository.Result;
import com.google.firebase.auth.FirebaseUser;

/** State of the Email Verification Page. */
public class VerifyViewModel extends AndroidViewModel {

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

    private final AuthRepository auth = new AuthRepository();
    private final VerificationRepository codes;

    private final State state = new State();
    private final MutableLiveData<State> stateLive = new MutableLiveData<>(state);
    /** Set once when the code is right: the role whose dashboard to open. */
    private final MutableLiveData<String> verifiedRole = new MutableLiveData<>();
    /** One-shot events for the screen. */
    private final MutableLiveData<Boolean> clearBoxes = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> codeSent = new MutableLiveData<>(false);

    private boolean started;
    private String uid;
    private String role;

    public VerifyViewModel(@NonNull Application app) {
        super(app);
        codes = new VerificationRepository(app);
    }

    public LiveData<State> getState() {
        return stateLive;
    }

    public LiveData<String> getVerifiedRole() {
        return verifiedRole;
    }

    public LiveData<Boolean> getClearBoxes() {
        return clearBoxes;
    }

    public LiveData<Boolean> getCodeSent() {
        return codeSent;
    }

    public void clearBoxesHandled() {
        clearBoxes.setValue(false);
    }

    public void codeSentHandled() {
        codeSent.setValue(false);
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

    private void loadPending() {
        codes.loadPending(uid, new Callback<VerificationRepository.Pending>() {
            @Override
            public void onSuccess(VerificationRepository.Pending p) {
                state.loading = false;
                if (p != null) {
                    state.expiresAt = p.expiresAt;
                    state.resendAt = p.sentAt + VerificationRepository.RESEND_COOLDOWN_MS;
                    state.locked = p.attempts >= VerificationRepository.MAX_ATTEMPTS;
                }
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
                        + VerificationRepository.RESEND_COOLDOWN_MS;
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

    public void verify(String code) {
        if (role == null || state.verifying) return;
        state.verifying = true;
        state.error = null;
        publish();
        codes.verify(uid, role, code, new Callback<Result>() {
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

    private void fail(String message) {
        state.loading = false;
        state.fatal = true;
        state.error = message;
        publish();
    }

    private void publish() {
        stateLive.setValue(state);
    }
}
