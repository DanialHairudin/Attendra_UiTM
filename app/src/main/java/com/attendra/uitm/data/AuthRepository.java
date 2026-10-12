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

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final UserRepository users = new UserRepository();

    public AuthRepository() {
        // Password reset emails follow the phone's language.
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
     * Register flow (CLAUDE.md 12.1 and 12.3):
     * 1. createUserWithEmailAndPassword
     * 2. write the role, profile + indexes in one multi-path update
     * 3. the caller opens the Verify screen, which creates and emails the
     *    6-digit code straight away (so the send result can be shown there)
     *
     * If step 2 fails, the new Auth account is deleted again. Otherwise the
     * email would be "taken" by an account that has no profile and can never log in.
     * The user stays signed in afterwards so the Verify screen can save the code
     * and set emailVerified.
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

    /** Where a signed-in user should go next. HomeRouter.open() follows it. */
    public static final class Route {
        /** "lecturer" or "student", from roles/{uid}. */
        public final String role;
        /** Email not verified yet → the caller shows the Verify screen. */
        public final boolean unverified;
        /** No preferred name yet → the Preferred Name Page before the dashboard. */
        public final boolean needsName;

        Route(String role, boolean unverified, boolean needsName) {
            this.role = role;
            this.unverified = unverified;
            this.needsName = needsName;
        }
    }

    /** Log in, then work out where to go (see resolveHome). */
    public void login(String email, String password, Callback<Route> callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        callback.onError(str(R.string.error_login_failed));
                    } else {
                        resolveHome(user, callback);
                    }
                })
                .addOnFailureListener(e -> callback.onError(AuthErrors.message(e)));
    }

    /**
     * Works out where a signed-in user should go: Verify screen, Preferred Name
     * Page, or the dashboard for their role.
     *
     * Firebase Auth's own isEmailVerified() is NOT used, because the app sends its
     * own 6-digit code (CLAUDE.md 12.3). The source of truth is emailVerified in
     * lecturers/{uid} or students/{uid}. Test accounts (isTest: true) skip both
     * verification and the Preferred Name Page (12.6).
     */
    public void resolveHome(FirebaseUser user, Callback<Route> callback) {
        loadRole(user, new Callback<String>() {
            @Override
            public void onSuccess(String role) {
                users.loadAccountState(user.getUid(), role, new Callback<UserRepository.AccountState>() {
                    @Override
                    public void onSuccess(UserRepository.AccountState s) {
                        TestWorld.remember(user.getUid(), s.isTest);
                        boolean unverified = !s.emailVerified && !s.isTest;
                        boolean needsName = !s.isTest && !s.hasPreferredName;
                        callback.onSuccess(new Route(role, unverified, needsName));
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /**
     * Reads the signed-in user's role from roles/{uid}. If the account has no role,
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
