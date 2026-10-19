package com.attendra.uitm.data;

import static com.attendra.uitm.AttendraApp.str;

import androidx.annotation.Nullable;

import com.attendra.uitm.R;

import com.attendra.uitm.model.Registration;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Wraps Firebase Authentication (Email/Password only) for the Auth screens. */
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

    /** Register flow. */
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
                            if (!r.isStudent()) {
                                undoSignUp(user, str(R.string.error_save_profile, message), callback);
                                return;
                            }
                            // A taken student ID is the likely cause; say so plainly.
                            users.isStudentIdTaken(r.studentId, user.getUid(), new Callback<Boolean>() {
                                @Override
                                public void onSuccess(Boolean taken) {
                                    undoSignUp(user, taken
                                            ? str(R.string.error_student_id_taken)
                                            : str(R.string.error_save_profile, message), callback);
                                }

                                @Override
                                public void onError(String ignored) {
                                    undoSignUp(user, str(R.string.error_save_profile, message), callback);
                                }
                            });
                        }
                    });
                })
                .addOnFailureListener(e -> callback.onError(AuthErrors.message(e)));
    }

    /** Removes the half-made account so the email can be used again, then reports the error. */
    private void undoSignUp(FirebaseUser user, String error, Callback<Void> callback) {
        user.delete().addOnCompleteListener(t -> {
            auth.signOut();
            callback.onError(error);
        });
    }

    /** Where a signed-in user should go next. */
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
     * Works out where a signed-in user should go: Verify screen, Preferred Name Page, or the
     * dashboard for their role.
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

    /** Reads the signed-in user's role from roles/{uid}. */
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

    /** Sends a password reset link. */
    public void sendPasswordReset(String email, Callback<Void> callback) {
        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(AuthErrors.message(e)));
    }
}
