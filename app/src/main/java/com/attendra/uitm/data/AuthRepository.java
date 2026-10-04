package com.attendra.uitm.data;

import static com.attendra.uitm.AttendraApp.str;

import androidx.annotation.Nullable;

import com.attendra.uitm.R;

import com.attendra.uitm.model.Registration;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Wraps Firebase Authentication (Email/Password only) for the Auth screens.
 */
public class AuthRepository {

    /**
     * Returned by login() instead of a role when the password was right but the
     * email is not verified yet.
     */
    public static final String UNVERIFIED = "unverified";

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final UserRepository users = new UserRepository();

    public AuthRepository() {
        // Verification and reset emails follow the phone's language.
        auth.useAppLanguage();
    }

    @Nullable
    public FirebaseUser currentUser() {
        return auth.getCurrentUser();
    }

    public void signOut() {
        auth.signOut();
    }

    /**
     * Register flow from CLAUDE.md section 4:
     * 1. createUserWithEmailAndPassword
     * 2. write the profile + indexes in one multi-path update
     * 3. sendEmailVerification
     *
     * If step 2 fails, the new Auth account is deleted again. Otherwise the
     * email would be "taken" by an account that has no profile and can never log in.
     * The user stays signed in afterwards so the Verify screen can resend the
     * email and reload the user.
     */
    public void register(Registration r, Callback<Void> callback) {
        auth.createUserWithEmailAndPassword(r.email, r.password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        callback.onError(str(R.string.error_create_account));
                        return;
                    }
                    users.createProfile(user.getUid(), r, new Callback<Void>() {
                        @Override
                        public void onSuccess(Void unused) {
                            // A failed send is not fatal: the Verify screen has Resend.
                            user.sendEmailVerification();
                            callback.onSuccess(null);
                        }

                        @Override
                        public void onError(String message) {
                            user.delete().addOnCompleteListener(t -> {
                                auth.signOut();
                                callback.onError(str(R.string.error_save_profile, message));
                            });
                        }
                    });
                })
                .addOnFailureListener(e -> callback.onError(AuthErrors.message(e)));
    }

    /**
     * Log in, then decide where to go. onSuccess receives:
     * - UNVERIFIED if the email is not verified (caller shows the Verify screen), or
     * - the role ("lecturer" / "student") read from users/{uid}/role.
     */
    public void login(String email, String password, Callback<String> callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        callback.onError(str(R.string.error_login_failed));
                    } else if (!user.isEmailVerified()) {
                        callback.onSuccess(UNVERIFIED);
                    } else {
                        loadRole(user, callback);
                    }
                })
                .addOnFailureListener(e -> callback.onError(AuthErrors.message(e)));
    }

    /**
     * Reads the signed-in user's role. If the account has no profile (or no role),
     * it signs out and reports an error, so nobody lands in the app half set up.
     */
    public void loadRole(FirebaseUser user, Callback<String> callback) {
        users.getRole(user.getUid(), new Callback<String>() {
            @Override
            public void onSuccess(String role) {
                if (Registration.ROLE_LECTURER.equals(role) || Registration.ROLE_STUDENT.equals(role)) {
                    callback.onSuccess(role);
                } else {
                    auth.signOut();
                    callback.onError(str(R.string.error_no_profile));
                }
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public void resendVerification(Callback<Void> callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError(str(R.string.error_session_ended));
            return;
        }
        user.sendEmailVerification()
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(AuthErrors.message(e)));
    }

    /**
     * user.reload() fetches the latest account state from Firebase. Without it,
     * isEmailVerified() keeps returning the old value even after the link is clicked.
     * Returns true if the email is now verified.
     */
    public void reloadAndCheckVerified(Callback<Boolean> callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError(str(R.string.error_session_ended));
            return;
        }
        user.reload()
                .addOnSuccessListener(unused -> {
                    FirebaseUser fresh = auth.getCurrentUser();
                    callback.onSuccess(fresh != null && fresh.isEmailVerified());
                })
                .addOnFailureListener(e -> callback.onError(AuthErrors.message(e)));
    }

    /**
     * Sends a password reset link. With email enumeration protection on (the
     * Firebase default), this succeeds even if no account uses the email, so the
     * screen must not claim that the account exists.
     */
    public void sendPasswordReset(String email, Callback<Void> callback) {
        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(AuthErrors.message(e)));
    }
}
