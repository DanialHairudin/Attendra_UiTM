package com.attendra.uitm.ui.student;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.Nullable;

import com.attendra.uitm.R;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.Db;
import com.attendra.uitm.databinding.PhoneCheckPageBinding;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.ui.BasePage;
import com.attendra.uitm.ui.auth.AuthPage;
import com.attendra.uitm.util.Formats;
import com.attendra.uitm.util.HomeRouter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.ServerValue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Students only, opened just before the Student Dashboard: one phone can be used by one student
 * account. It opens Home, asks to move the account, or shows "Phone already linked".
 */
public class PhoneCheckPage extends BasePage {

    /** After moving to another phone, the student must wait this long to move again (7 days). */
    public static final long MOVE_WAIT_MS = 7L * 24 * 60 * 60 * 1000;

    private PhoneCheckPageBinding binding;
    private String uid;
    private String deviceHash;
    private DataSnapshot profile;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = PhoneCheckPageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        padForSystemBars(binding.phoneRoot);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            HomeRouter.openSplash(this);
            return;
        }
        uid = user.getUid();

        // SECTION 1: read the phone ID
        deviceHash = deviceHash(this);

        readProfile();
    }

    /**
     * The phone ID as a SHA-256 hex text (64 characters). ANDROID_ID itself is never saved.
     */
    // Based on: Android docs "Best practices for unique identifiers" (ANDROID_ID)
    @SuppressLint("HardwareIds")
    public static String deviceHash(Context context) {
        String androidId = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(String.valueOf(androidId).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // Every Android version has SHA-256, so this can't happen.
            throw new IllegalStateException(e);
        }
    }

    /** Reads the student's profile once. */
    private void readProfile() {
        showLoading();
        // SECTION 2: read students/{uid}
        Db.readOnce(Db.root().child("students").child(uid), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                if (isFinishing()) return;
                profile = snapshot;
                // The tester account shares a phone with real accounts, so it is not checked.
                if (Boolean.TRUE.equals(snapshot.child("isTest").getValue(Boolean.class))) {
                    openHome();
                    return;
                }
                readPhone();
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });
    }

    /** Reads who uses this phone. */
    // Based on: Firebase docs "Read and Write Data on Android" (read data once)
    private void readPhone() {
        // SECTION 3: read devices/{deviceHash} once
        Db.readOnce(Db.root().child("devices").child(deviceHash), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                if (isFinishing()) return;
                decide(snapshot.child("uid").getValue(String.class));
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });
    }

    /** Decides what to do with this phone. owner = the student who uses it, or null. */
    private void decide(@Nullable String owner) {
        // SECTION 4: decide
        String myPhone = profile.child("deviceHash").getValue(String.class);
        if (uid.equals(owner) && deviceHash.equals(myPhone)) {
            // b) this phone belongs to this student
            openHome();
        } else if (owner != null && !uid.equals(owner)) {
            // c) this phone belongs to another student
            showAlreadyLinked();
        } else if (myPhone == null || deviceHash.equals(myPhone)) {
            // a) nobody uses this phone and the student has no other phone
            linkPhone(myPhone == null);
        } else {
            // d) the student already has a different phone
            readOldPhone(myPhone);
        }
    }

    /** Links this phone to the student. newLink = the student had no phone yet. */
    // Based on: Firebase docs "Read and Write Data on Android" (updateChildren)
    private void linkPhone(boolean newLink) {
        Map<String, Object> device = new HashMap<>();
        device.put("uid", uid);
        device.put("linkedAt", ServerValue.TIMESTAMP);

        Map<String, Object> updates = new HashMap<>();
        updates.put("devices/" + deviceHash, device);
        if (newLink) {
            updates.put("students/" + uid + "/deviceHash", deviceHash);
        }
        Db.write(updates, new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                openHome();
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });
    }

    /** Checks if the old phone is still linked to this student, then asks to move. */
    private void readOldPhone(String oldHash) {
        Db.readOnce(Db.root().child("devices").child(oldHash), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                if (isFinishing()) return;
                // The old link may already be removed in the Firebase console (lost phone).
                if (uid.equals(snapshot.child("uid").getValue(String.class))) {
                    askToMove(oldHash);
                } else {
                    askToMove(null);
                }
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });
    }

    /** "Move it to this phone?" The student then types a code sent by email. */
    private void askToMove(@Nullable String oldHash) {
        Long movedAt = profile.child("deviceMovedAt").getValue(Long.class);
        if (movedAt != null && System.currentTimeMillis() < movedAt + MOVE_WAIT_MS) {
            showState(R.string.phone_move_wait_title, getString(R.string.phone_move_wait_body,
                    Formats.shortDate(movedAt + MOVE_WAIT_MS)));
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.phone_move_question)
                .setCancelable(false)
                .setNegativeButton(R.string.back_to_login, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        logOut();
                    }
                })
                .setPositiveButton(R.string.phone_move_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        openCodePage(oldHash);
                    }
                })
                .show();
    }

    /** Opens the 6-digit email code page. The right code saves the move. */
    private void openCodePage(@Nullable String oldHash) {
        Intent intent = new Intent(this, AuthPage.class);
        intent.putExtra(AuthPage.EXTRA_MODE, AuthPage.MODE_MOVE_PHONE);
        intent.putExtra(AuthPage.EXTRA_EMAIL, profile.child("email").getValue(String.class));
        intent.putExtra(AuthPage.EXTRA_NEW_HASH, deviceHash);
        intent.putExtra(AuthPage.EXTRA_OLD_HASH, oldHash);
        startActivity(intent);
        finish();
    }

    /** The database changes that move the student from the old phone to the new one. */
    public static Map<String, Object> moveUpdates(String uid, String newHash,
                                                  @Nullable String oldHash) {
        Map<String, Object> device = new HashMap<>();
        device.put("uid", uid);
        device.put("linkedAt", ServerValue.TIMESTAMP);

        Map<String, Object> updates = new HashMap<>();
        if (oldHash != null) {
            updates.put("devices/" + oldHash, null); // Null deletes the old link
        }
        updates.put("devices/" + newHash, device);
        updates.put("students/" + uid + "/deviceHash", newHash);
        updates.put("students/" + uid + "/deviceMovedAt", ServerValue.TIMESTAMP);
        return updates;
    }

    // ------------------------------------------------------------- what is on the screen

    /** Opens the Student Dashboard. */
    private void openHome() {
        HomeRouter.openDashboard(this, Registration.ROLE_STUDENT);
    }

    /** Signs out and goes back to the first page. */
    private void logOut() {
        FirebaseAuth.getInstance().signOut();
        HomeRouter.openSplash(this);
    }

    /** "Phone already linked". The student is signed out straight away. */
    private void showAlreadyLinked() {
        FirebaseAuth.getInstance().signOut();
        showState(R.string.phone_linked_title, getString(R.string.phone_linked_body));
    }

    /** Shows the loading circle. */
    private void showLoading() {
        binding.progressPhone.setVisibility(View.VISIBLE);
        binding.state.getRoot().setVisibility(View.GONE);
    }

    /** Shows a message with the "Back to log in" button. */
    private void showState(int title, String body) {
        binding.progressPhone.setVisibility(View.GONE);
        binding.state.getRoot().setVisibility(View.VISIBLE);
        binding.state.stateIcon.setImageResource(R.drawable.ic_lock);
        binding.state.stateTitle.setText(title);
        binding.state.stateBody.setText(body);
        binding.state.statePrimary.setText(R.string.back_to_login);
        binding.state.statePrimary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logOut();
            }
        });
        binding.state.stateSecondary.setVisibility(View.GONE);
    }

    /** No internet, or the database said no: try again or go back. */
    private void showError(String message) {
        if (isFinishing()) return;
        showState(R.string.phone_error_title, message);
        binding.state.stateIcon.setImageResource(R.drawable.ic_wifi_off);
        binding.state.statePrimary.setText(R.string.try_again);
        binding.state.statePrimary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                readProfile();
            }
        });
        binding.state.stateSecondary.setVisibility(View.VISIBLE);
        binding.state.stateSecondary.setText(R.string.back_to_login);
        binding.state.stateSecondary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logOut();
            }
        });
    }
}
