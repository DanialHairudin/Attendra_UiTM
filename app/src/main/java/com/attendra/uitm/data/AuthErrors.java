package com.attendra.uitm.data;

import static com.attendra.uitm.AttendraApp.str;

import com.attendra.uitm.R;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.database.DatabaseError;

/**
 * Turns Firebase exceptions into short, formal messages for the user.
 * All wording lives in res/values/strings.xml (the error_* strings).
 *
 * The order matters: the specific exception types are subclasses of
 * FirebaseAuthException, so they are checked first.
 */
public final class AuthErrors {

    private AuthErrors() {
    }

    /** Message for an Authentication (or database write) exception. */
    public static String message(Exception e) {
        if (e instanceof FirebaseNetworkException) {
            return str(R.string.error_network);
        }
        if (e instanceof FirebaseTooManyRequestsException) {
            return str(R.string.error_too_many_requests);
        }
        if (e instanceof FirebaseAuthUserCollisionException) {
            return str(R.string.error_email_in_use);
        }
        if (e instanceof FirebaseAuthWeakPasswordException) {
            return str(R.string.error_password_short);
        }
        if (e instanceof FirebaseAuthInvalidUserException) {
            return str(R.string.error_account_unavailable);
        }
        if (e instanceof FirebaseAuthInvalidCredentialsException) {
            // Firebase gives the same error for a wrong email and a wrong password
            // (email enumeration protection), so we can't say which one it was.
            return str(R.string.error_wrong_credentials);
        }
        if (e instanceof FirebaseAuthException
                && "ERROR_OPERATION_NOT_ALLOWED".equals(((FirebaseAuthException) e).getErrorCode())) {
            return str(R.string.error_sign_in_disabled);
        }
        // Database write failures arrive as a DatabaseException whose message
        // contains "Permission denied" when the security rules refuse them.
        String msg = e.getMessage();
        if (msg != null && msg.toLowerCase().contains("permission denied")) {
            return str(R.string.error_permission_denied);
        }
        return str(R.string.error_generic);
    }

    /** Message for a failed database read. */
    public static String database(DatabaseError error) {
        switch (error.getCode()) {
            case DatabaseError.PERMISSION_DENIED:
                return str(R.string.error_permission_denied);
            case DatabaseError.DISCONNECTED:
            case DatabaseError.NETWORK_ERROR:
                return str(R.string.error_network);
            default:
                return str(R.string.error_generic);
        }
    }
}
