package com.attendra.uitm.data;

import static com.attendra.uitm.AttendraApp.str;

import android.content.Context;

import com.attendra.uitm.BuildConfig;
import com.attendra.uitm.R;
import com.attendra.uitm.util.NameSuggestions;
import com.google.firebase.database.DataSnapshot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** 6-digit email verification codes. */
public class VerificationRepository {

    public static final int MAX_ATTEMPTS = 5;
    /** Resend is disabled for this long after each send. */
    public static final long RESEND_COOLDOWN_MS = 30_000;

    /** Result of checking a typed code. */
    public enum Result { OK, WRONG, TOO_MANY, EXPIRED }

    /** The code that is waiting to be entered, as stored in the database. */
    public static class Pending {
        public final long expiresAt;
        public final long sentAt;
        public final int attempts;

        Pending(long expiresAt, long sentAt, int attempts) {
            this.expiresAt = expiresAt;
            this.sentAt = sentAt;
            this.attempts = attempts;
        }
    }

    /** SecureRandom, not Random: its numbers can't be predicted from earlier ones. */
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MailSender mail;

    public VerificationRepository(Context context) {
        mail = new MailSender(context);
    }

    private static String codePath(String uid) {
        return "verificationCodes/" + uid;
    }

    /** Creates a NEW code, saves its hash and emails it. */
    public void sendNewCode(String uid, String role, Callback<Long> callback) {
        Db.readOnce(Db.root().child(UserRepository.profileNode(role)).child(uid),
                new Callback<DataSnapshot>() {
                    @Override
                    public void onSuccess(DataSnapshot profile) {
                        String name = profile.child("name").getValue(String.class);
                        String email = profile.child("email").getValue(String.class);
                        if (email == null) {
                            callback.onError(str(R.string.error_no_profile));
                            return;
                        }
                        // {{NAME}} is the first name suggestion ("Danial"): no preferred name
                        // exists yet at this point.
                        saveAndSend(uid, role, NameSuggestions.first(name), email, callback);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
    }

    private void saveAndSend(String uid, String role, String name, String email,
                             Callback<Long> callback) {
        String code = newCode();
        long now = System.currentTimeMillis();
        long expiresAt = now + BuildConfig.CODE_TTL_MINUTES * 60_000L;

        Map<String, Object> record = new HashMap<>();
        record.put("codeHash", hash(uid, code));
        record.put("expiresAt", expiresAt);
        record.put("sentAt", now);
        record.put("attempts", 0);
        Map<String, Object> updates = new HashMap<>();
        updates.put(codePath(uid), record);

        Db.write(updates, new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                mail.sendVerificationCode(email, name, role, code, new Callback<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        callback.onSuccess(expiresAt);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
            }

            @Override
            public void onError(String message) {
                // The database could not be reached, so the email can't be sent either.
                callback.onError(str(R.string.code_send_failed));
            }
        });
    }

    /** Reads the waiting code (for the countdown). */
    public void loadPending(String uid, Callback<Pending> callback) {
        Db.readOnce(Db.root().child(codePath(uid)), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                callback.onSuccess(snapshot.exists() ? toPending(snapshot) : null);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /** Checks a typed code, in this order. */
    public void verify(String uid, String role, String code, Callback<Result> callback) {
        Db.readOnce(Db.root().child(codePath(uid)), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                String savedHash = snapshot.child("codeHash").getValue(String.class);
                Pending pending = snapshot.exists() ? toPending(snapshot) : null;
                if (savedHash == null || pending == null
                        || System.currentTimeMillis() >= pending.expiresAt) {
                    callback.onSuccess(Result.EXPIRED);
                    return;
                }
                if (pending.attempts >= MAX_ATTEMPTS) {
                    callback.onSuccess(Result.TOO_MANY);
                    return;
                }

                Map<String, Object> updates = new HashMap<>();
                final Result result;
                if (savedHash.equals(hash(uid, code))) {
                    updates.put(UserRepository.profileNode(role) + "/" + uid + "/emailVerified", true);
                    updates.put(codePath(uid), null); // Null deletes the node
                    result = Result.OK;
                } else {
                    int attempts = pending.attempts + 1;
                    updates.put(codePath(uid) + "/attempts", attempts);
                    result = attempts >= MAX_ATTEMPTS ? Result.TOO_MANY : Result.WRONG;
                }
                Db.write(updates, new Callback<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        callback.onSuccess(result);
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

    private static Pending toPending(DataSnapshot s) {
        Long expiresAt = s.child("expiresAt").getValue(Long.class);
        Long sentAt = s.child("sentAt").getValue(Long.class);
        Long attempts = s.child("attempts").getValue(Long.class);
        return new Pending(expiresAt == null ? 0 : expiresAt,
                sentAt == null ? 0 : sentAt,
                attempts == null ? 0 : attempts.intValue());
    }

    /** Random 000000–999999. Leading zeros are kept, so it is always 6 digits. */
    private static String newCode() {
        return String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
    }

    /** SHA-256 of "uid:code", written as 64 lowercase hex characters. */
    static String hash(String uid, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((uid + ":" + code).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) hex.append(String.format(Locale.ROOT, "%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // Every Android version has SHA-256, so this can't happen.
            throw new IllegalStateException(e);
        }
    }
}
