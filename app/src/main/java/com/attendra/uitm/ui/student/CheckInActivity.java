package com.attendra.uitm.ui.student;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;

import com.attendra.uitm.R;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.CheckInRepository;
import com.attendra.uitm.data.Db;
import com.attendra.uitm.data.SeedCache;
import com.attendra.uitm.data.Subscriptions;
import com.attendra.uitm.databinding.ActivityCheckInBinding;
import com.attendra.uitm.model.Attempt;
import com.attendra.uitm.model.Attendance;
import com.attendra.uitm.model.AttendanceRecord;
import com.attendra.uitm.model.Session;
import com.attendra.uitm.ui.BaseActivity;
import com.attendra.uitm.ui.common.LocationPermission;
import com.attendra.uitm.ui.common.SessionText;
import com.attendra.uitm.ui.session.SessionTypes;
import com.attendra.uitm.util.Formats;
import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;

import java.util.Locale;

/**
 * Check-in page (CLAUDE.md section 6). No map: the session, a status card and
 * one big button. When the student taps "Check in now", the checks run in
 * this order, each in its own method below:
 *
 *  0. Online?               .info/connected, else the Offline state
 *  1. Session open?         status open and server time inside start–end, else Session closed
 *  2. Eligible?             the student's group, or club membership, else Not in this session
 *     (already has a record for this session → straight to the Result page, nothing written)
 *  3. Location permission?  else the Permission state (foreground only)
 *  4. GPS on?               else the GPS off state
 *  5. One fresh fix         getCurrentLocation(PRIORITY_HIGH_ACCURACY), 15 s timeout
 *  6. Mock location?        → rejected MOCK_LOCATION (logged in attempts)
 *  7. Accuracy ≤ 50 m?      else the Low accuracy state; nothing is written
 *  8. Inside the radius?    Location.distanceBetween, else rejected OUTSIDE_RADIUS (logged)
 *  9. Write the record      present if server time ≤ lateAfter, else late (+ attempts entry)
 *
 * Location is read ONCE, in step 5, only after the tap. It is never tracked.
 */
public class CheckInActivity extends BaseActivity {

    private static final String TAG = "CheckIn";
    private static final String EXTRA_SESSION = "session_id";
    /** Section 6 step 7: a fix less precise than this is not trusted. */
    private static final float MAX_ACCURACY_M = 50f;
    /** Section 6 step 5: give up on the fix after 15 seconds. */
    private static final long FIX_TIMEOUT_MS = 15_000;

    public static void open(Context from, String sessionId) {
        from.startActivity(new Intent(from, CheckInActivity.class)
                .putExtra(EXTRA_SESSION, sessionId));
    }

    /** The full-page states that replace the check-in page when a check fails. */
    private enum State { OFFLINE, CLOSED, NOT_ELIGIBLE, PERMISSION, GPS_OFF, LOW_ACCURACY }

    private ActivityCheckInBinding binding;
    private final CheckInRepository repo = new CheckInRepository();
    private final Subscriptions subs = new Subscriptions();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FusedLocationProviderClient fused;
    @Nullable
    private CancellationTokenSource fixCancel;

    private String sessionId;
    private String uid;
    @Nullable
    private Session session;
    /** Phone clock minus server clock, from .info/serverTimeOffset. */
    private long serverOffset;
    /** From .info/connected. Starts true so a slow first answer doesn't block. */
    private boolean connected = true;
    private boolean busy;
    @Nullable
    private State shownState;
    /** True once Android said "don't ask again" for location. */
    private boolean permissionBlocked;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            showCountdown();
            handler.postDelayed(this, 1000);
        }
    };

    /** Android's own permission prompt (fine + coarse, never background). */
    private final ActivityResultLauncher<String[]> askLocation = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (LocationPermission.granted(this)) {
                    hideState();
                    startCheckIn();
                } else {
                    // No rationale after a refusal = "Don't ask again": only Settings can fix it.
                    permissionBlocked = !shouldShowRequestPermissionRationale(
                            android.Manifest.permission.ACCESS_FINE_LOCATION);
                    showState(State.PERMISSION, null);
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCheckInBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        padForSystemBars(binding.checkInRoot);

        sessionId = getIntent().getStringExtra(EXTRA_SESSION);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (sessionId == null || user == null) {
            finish();
            return;
        }
        uid = user.getUid();
        fused = LocationServices.getFusedLocationProviderClient(this);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnCheckIn.setOnClickListener(v -> startCheckIn());
        binding.state.statePrimary.setOnClickListener(v -> onStatePrimary());
        binding.state.stateSecondary.setOnClickListener(v -> finish());
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Server clock: the window and late checks use server time, not the phone's.
        subs.listen(Db.get().getReference(".info/serverTimeOffset"), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snap) {
                Long offset = snap.getValue(Long.class);
                serverOffset = offset != null ? offset : 0;
            }

            @Override
            public void onError(String message) {
            }
        });
        // Offline state (section 6): no check-in without a server connection.
        subs.listen(Db.get().getReference(".info/connected"), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snap) {
                connected = Boolean.TRUE.equals(snap.getValue(Boolean.class));
            }

            @Override
            public void onError(String message) {
            }
        });
        SeedCache.load(new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                listenToSession();
            }

            @Override
            public void onError(String message) {
                listenToSession(); // the page still works, course names just show IDs
            }
        });
        handler.post(tick);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Back from Android settings: if what was missing is now fixed, return to the page.
        if (shownState == State.GPS_OFF && gpsOn()) hideState();
        if (shownState == State.PERMISSION && LocationPermission.granted(this)) hideState();
    }

    @Override
    protected void onStop() {
        super.onStop();
        subs.clear();
        handler.removeCallbacks(tick);
        if (fixCancel != null) fixCancel.cancel();
    }

    /** Live session, so the page knows at once if the lecturer closes it. */
    private void listenToSession() {
        subs.listen(Db.root().child("sessions").child(sessionId), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snap) {
                session = snap.exists() ? snap.getValue(Session.class) : null;
                if (session != null) session.id = sessionId;
                renderSession();
            }

            @Override
            public void onError(String message) {
                renderSession();
            }
        });
    }

    private long serverNow() {
        return System.currentTimeMillis() + serverOffset;
    }

    // ------------------------------------------------------------- the page

    private void renderSession() {
        binding.progressLoad.setVisibility(View.GONE);
        // Already over (closed by the lecturer, or past its end time): show
        // "Session closed" straight away instead of a "Ready" button that can
        // only fail. Check 1 still runs again when the button is tapped.
        if (session == null || !session.isOpen() || serverNow() >= session.end) {
            showState(State.CLOSED, null);
            return;
        }
        binding.scrollMain.setVisibility(shownState == null ? View.VISIBLE : View.GONE);
        binding.btnCheckIn.setVisibility(shownState == null ? View.VISIBLE : View.GONE);
        binding.txtType.setText(SessionTypes.name(session.type));
        binding.txtTimeRange.setText(Formats.timeRange(session.start, session.end));
        binding.txtTitle.setText(session.title);
        binding.txtCourse.setText(SessionText.course(session));
        binding.txtVenue.setText(SessionText.room(this, session));
        if (!busy) showReady();
        showCountdown();
    }

    /** "closes in 14:32" in the app bar, from the server clock. */
    private void showCountdown() {
        if (session == null) {
            binding.txtClosesIn.setText("");
            return;
        }
        long now = serverNow();
        if (now < session.start) {
            binding.txtClosesIn.setText(getString(R.string.opens_at, Formats.time(session.start)));
        } else if (now < session.end && session.isOpen()) {
            long secs = (session.end - now) / 1000;
            String left = secs >= 3600
                    ? String.format(Locale.ROOT, "%d:%02d:%02d", secs / 3600, secs / 60 % 60, secs % 60)
                    : String.format(Locale.ROOT, "%d:%02d", secs / 60, secs % 60);
            binding.txtClosesIn.setText(getString(R.string.closes_in, left));
        } else {
            binding.txtClosesIn.setText(R.string.status_closed);
        }
    }

    private void showReady() {
        setStatus(R.drawable.ic_target, R.color.goldText, R.color.gold_tint,
                getString(R.string.check_in_ready_title), getString(R.string.check_in_ready_body));
    }

    /** Spinner in the status card while a step is running; the button is disabled. */
    private void showBusy(@StringRes int title) {
        busy = true;
        binding.btnCheckIn.setEnabled(false);
        binding.progressCheck.setVisibility(View.VISIBLE);
        binding.imgStatus.setVisibility(View.INVISIBLE);
        binding.txtStatusTitle.setText(title);
        binding.txtStatusBody.setText(R.string.check_in_busy_body);
    }

    private void notBusy() {
        busy = false;
        binding.btnCheckIn.setEnabled(true);
        binding.progressCheck.setVisibility(View.GONE);
        binding.imgStatus.setVisibility(View.VISIBLE);
    }

    /** A problem that is shown in the status card (e.g. the save failed). */
    private void showProblem(String message) {
        notBusy();
        setStatus(R.drawable.ic_close, R.color.rejected, R.color.rejected_tint,
                getString(R.string.check_in_failed_title), message);
    }

    private void setStatus(@DrawableRes int icon, int iconColor, int bgTint,
                           String title, String body) {
        binding.imgStatus.setImageResource(icon);
        binding.imgStatus.setColorFilter(ContextCompat.getColor(this, iconColor));
        binding.statusIconBg.setBackgroundTintList(ContextCompat.getColorStateList(this, bgTint));
        binding.txtStatusTitle.setText(title);
        binding.txtStatusBody.setText(body);
    }

    // --------------------------------------------------------- the checks

    /** "Check in now" (and every Try again). Runs the checks in section 6 order. */
    private void startCheckIn() {
        if (busy) return;
        // 0. Offline: don't even try.
        if (!connected) {
            showState(State.OFFLINE, null);
            return;
        }
        // 1. Session exists, is open, and the server time is inside its window.
        Session s = session;
        if (s == null || !s.isCheckInOpen(serverNow())) {
            showState(State.CLOSED, null);
            return;
        }
        showBusy(R.string.check_in_step_session);
        checkEligible(s);
    }

    /** 2. In one of the session's groups, or a member of the club. */
    private void checkEligible(Session s) {
        repo.isEligible(s, uid, new Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean eligible) {
                if (!eligible) {
                    notBusy();
                    showState(State.NOT_ELIGIBLE, null);
                    return;
                }
                checkNotAlreadyRecorded(s);
            }

            @Override
            public void onError(String message) {
                showProblem(message);
            }
        });
    }

    /** Already checked in (or marked by the lecturer)? Show that; never overwrite it. */
    private void checkNotAlreadyRecorded(Session s) {
        repo.existingRecord(s.id, uid, new Callback<AttendanceRecord>() {
            @Override
            public void onSuccess(AttendanceRecord existing) {
                if (existing != null) {
                    notBusy();
                    CheckInResultActivity.openExisting(CheckInActivity.this, s, existing);
                    finish();
                    return;
                }
                checkPermissionAndGps(s);
            }

            @Override
            public void onError(String message) {
                showProblem(message);
            }
        });
    }

    /** 3. Location permission, then 4. GPS switched on. */
    private void checkPermissionAndGps(Session s) {
        if (!LocationPermission.granted(this)) {
            notBusy();
            showState(State.PERMISSION, null);
            return;
        }
        if (!gpsOn()) {
            notBusy();
            showState(State.GPS_OFF, null);
            return;
        }
        getFreshFix(s);
    }

    private boolean gpsOn() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        return lm != null && LocationManagerCompat.isLocationEnabled(lm);
    }

    /**
     * 5. ONE fresh fix: high accuracy, never a cached old location
     * (maxUpdateAge 0), and at most 15 s. A null result means no fix in time.
     */
    @SuppressLint("MissingPermission") // checked in step 3 just before this
    private void getFreshFix(Session s) {
        showBusy(R.string.check_in_step_location);
        CurrentLocationRequest request = new CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setDurationMillis(FIX_TIMEOUT_MS)
                .setMaxUpdateAgeMillis(0)
                .build();
        fixCancel = new CancellationTokenSource();
        fused.getCurrentLocation(request, fixCancel.getToken())
                .addOnSuccessListener(location -> {
                    if (isFinishing()) return;
                    if (location == null) {
                        notBusy();
                        showState(State.LOW_ACCURACY, null);
                        return;
                    }
                    checkFix(s, location);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "getCurrentLocation failed", e);
                    if (isFinishing()) return;
                    notBusy();
                    showState(State.LOW_ACCURACY, null);
                });
    }

    /** 6. Mock, 7. accuracy, 8. distance. */
    private void checkFix(Session s, Location fix) {
        double lat = fix.getLatitude();
        double lng = fix.getLongitude();
        float accuracy = fix.getAccuracy();

        // 6. A fake-GPS app is providing the location → rejected and logged.
        if (isMock(fix)) {
            showBusy(R.string.check_in_step_saving);
            repo.logRejected(s, uid, Attempt.REASON_MOCK, lat, lng, null, accuracy,
                    thenShowRejected(s, Attempt.REASON_MOCK, -1, accuracy));
            return;
        }

        // 7. Not precise enough to decide → ask to retry; nothing is written.
        if (accuracy > MAX_ACCURACY_M) {
            notBusy();
            showState(State.LOW_ACCURACY, Math.round(accuracy));
            return;
        }

        // 8. Our own point-in-circle check (no GeofencingClient).
        float[] result = new float[1];
        Location.distanceBetween(lat, lng, s.lat, s.lng, result);
        double distance = result[0];
        if (distance > s.radius) {
            showBusy(R.string.check_in_step_saving);
            repo.logRejected(s, uid, Attempt.REASON_OUTSIDE, lat, lng, distance, accuracy,
                    thenShowRejected(s, Attempt.REASON_OUTSIDE, distance, accuracy));
            return;
        }

        writeRecord(s, lat, lng, distance, accuracy);
    }

    /**
     * After logging a rejected attempt, show the Rejected result. The result
     * is shown even if the log could not be saved: the student still needs to
     * know why they were not checked in.
     */
    private Callback<Void> thenShowRejected(Session s, String reason, double distance,
                                            float accuracy) {
        return new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                show();
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Could not log rejected attempt: " + message);
                show();
            }

            private void show() {
                CheckInResultActivity.openRejected(CheckInActivity.this, s, reason,
                        distance, accuracy);
                finish();
            }
        };
    }

    /** API 31+: isMock(). Older: isFromMockProvider(). */
    @SuppressWarnings("deprecation")
    private static boolean isMock(Location fix) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? fix.isMock() : fix.isFromMockProvider();
    }

    /**
     * 9. Present if the server time is at or before lateAfter, otherwise late.
     * The session window is checked again first, because getting the fix can
     * take up to 15 s. The C12 rules check the same things on the server.
     */
    private void writeRecord(Session s, double lat, double lng, double distance, float accuracy) {
        long now = serverNow();
        Session live = session != null ? session : s;
        if (!live.isCheckInOpen(now)) {
            notBusy();
            showState(State.CLOSED, null);
            return;
        }
        String status = now <= live.lateAfter ? Attendance.PRESENT : Attendance.LATE;
        showBusy(R.string.check_in_step_saving);
        repo.writeAccepted(live, uid, status, lat, lng, distance, accuracy, new Callback<Long>() {
            @Override
            public void onSuccess(Long savedAt) {
                CheckInResultActivity.openAccepted(CheckInActivity.this, live, status,
                        savedAt, distance, accuracy);
                finish();
            }

            @Override
            public void onError(String message) {
                showProblem(message);
            }
        });
    }

    // ------------------------------------------------------------- states

    /**
     * Replaces the page with a state. detail is the accuracy in metres for
     * LOW_ACCURACY (null when there was no fix at all).
     */
    private void showState(State state, @Nullable Integer detail) {
        shownState = state;
        binding.scrollMain.setVisibility(View.GONE);
        binding.btnCheckIn.setVisibility(View.GONE);
        binding.progressLoad.setVisibility(View.GONE);
        binding.state.getRoot().setVisibility(View.VISIBLE);
        binding.state.stateSecondary.setVisibility(View.VISIBLE);

        switch (state) {
            case OFFLINE:
                fillState(R.drawable.ic_wifi_off, R.string.state_offline_title,
                        getString(R.string.state_offline_body), R.string.try_again);
                break;
            case CLOSED:
                if (session == null) {
                    fillState(R.drawable.ic_clock, R.string.state_closed_title,
                            getString(R.string.state_closed_missing), R.string.back_to_home);
                } else if (session.isOpen() && serverNow() < session.start) {
                    fillState(R.drawable.ic_clock, R.string.state_not_open_title,
                            getString(R.string.state_not_open_body, session.title,
                                    Formats.time(session.start)), R.string.back_to_home);
                } else {
                    fillState(R.drawable.ic_clock, R.string.state_closed_title,
                            getString(R.string.state_closed_body, session.title,
                                    Formats.time(session.end)), R.string.back_to_home);
                }
                binding.state.stateSecondary.setVisibility(View.GONE);
                break;
            case NOT_ELIGIBLE:
                fillState(R.drawable.ic_lock, R.string.state_not_eligible_title,
                        getString(R.string.state_not_eligible_body), R.string.back_to_home);
                binding.state.stateSecondary.setVisibility(View.GONE);
                break;
            case PERMISSION:
                fillState(R.drawable.ic_pin, R.string.state_permission_title,
                        getString(permissionBlocked ? R.string.state_permission_blocked_body
                                : R.string.state_permission_body),
                        permissionBlocked ? R.string.open_settings : R.string.allow_location);
                break;
            case GPS_OFF:
                fillState(R.drawable.ic_target, R.string.state_gps_off_title,
                        getString(R.string.state_gps_off_body), R.string.open_settings);
                break;
            case LOW_ACCURACY:
                fillState(R.drawable.ic_target, R.string.state_low_accuracy_title,
                        detail != null ? getString(R.string.state_low_accuracy_body, detail)
                                : getString(R.string.state_no_fix_body), R.string.try_again);
                break;
        }
    }

    private void fillState(@DrawableRes int icon, @StringRes int title, String body,
                           @StringRes int button) {
        binding.state.stateIcon.setImageResource(icon);
        binding.state.stateTitle.setText(title);
        binding.state.stateBody.setText(body);
        binding.state.statePrimary.setText(button);
    }

    private void hideState() {
        shownState = null;
        binding.state.getRoot().setVisibility(View.GONE);
        binding.scrollMain.setVisibility(View.VISIBLE);
        binding.btnCheckIn.setVisibility(View.VISIBLE);
        showReady();
    }

    private void onStatePrimary() {
        if (shownState == null) return;
        switch (shownState) {
            case OFFLINE:
            case LOW_ACCURACY:
                hideState();
                startCheckIn();
                break;
            case CLOSED:
            case NOT_ELIGIBLE:
                finish();
                break;
            case PERMISSION:
                if (permissionBlocked) {
                    // Android won't ask again; the switch is in the app's settings.
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", getPackageName(), null)));
                } else {
                    askLocation.launch(LocationPermission.PERMISSIONS);
                }
                break;
            case GPS_OFF:
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                break;
        }
    }
}
