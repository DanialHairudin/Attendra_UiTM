package com.attendra.uitm.ui.student;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
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

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;

import com.attendra.uitm.R;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.CheckInJava;
import com.attendra.uitm.data.Db;
import com.attendra.uitm.data.SeedCache;
import com.attendra.uitm.data.Subscriptions;
import com.attendra.uitm.databinding.CheckInPageBinding;
import com.attendra.uitm.map.VenuePinMap;
import com.attendra.uitm.model.Attempt;
import com.attendra.uitm.model.Attendance;
import com.attendra.uitm.model.AttendanceRecord;
import com.attendra.uitm.model.Session;
import com.attendra.uitm.ui.BasePage;
import com.attendra.uitm.ui.common.LocationPermission;
import com.attendra.uitm.ui.common.SessionText;
import com.attendra.uitm.ui.session.SessionTypes;
import com.attendra.uitm.util.Formats;
import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;

import org.osmdroid.util.GeoPoint;

import java.util.Locale;
import java.util.Map;

/** Check-in page: the session, a read-only venue map, a status card and one big button. */
public class CheckInPage extends BasePage {

    private static final String TAG = "CheckIn";
    private static final String EXTRA_SESSION = "session_id";
    /** Section 6 step 7: a fix less precise than this is not trusted. */
    private static final float MAX_ACCURACY_M = 50f;
    /** Section 6 step 5: give up on the fix after 15 seconds. */
    private static final long FIX_TIMEOUT_MS = 15_000;

    /** Opens the check-in page of one session. */
    public static void open(Context from, String sessionId) {
        from.startActivity(new Intent(from, CheckInPage.class)
                .putExtra(EXTRA_SESSION, sessionId));
    }

    /** The full-page states that replace the check-in page when a check fails. */
    private enum State { OFFLINE, CLOSED, NOT_ELIGIBLE, PERMISSION, GPS_OFF, LOW_ACCURACY }

    private CheckInPageBinding binding;
    private final CheckInJava checkInJava = new CheckInJava();
    private final Subscriptions subs = new Subscriptions();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FusedLocationProviderClient fused;
    @Nullable
    private CancellationTokenSource fixCancel;

    private String sessionId;
    private String uid;
    /** This phone's ID (see PhoneCheckPage), saved with the check-in. */
    private String deviceHash;
    @Nullable
    private Session session;
    /** Phone clock minus server clock, from .info/serverTimeOffset. */
    private long serverOffset;
    /** From .info/connected. */
    private boolean connected = true;
    private boolean busy;
    @Nullable
    private State shownState;
    /** True once Android said "don't ask again" for location. */
    private boolean permissionBlocked;
    /** Read-only venue map, created when the session first loads. */
    @Nullable
    private VenuePinMap venueMap;
    /** True after the student tapped "Show my location" (preview only, never saved). */
    private boolean showingMe;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            showCountdown();
            showMyDistance();
            handler.postDelayed(this, 1000);
        }
    };

    /** Permission prompt for "Show my location". */
    private final ActivityResultLauncher<String[]> askMapLocation = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), new ActivityResultCallback<Map<String, Boolean>>() {
                @Override
                public void onActivityResult(Map<String, Boolean> result) {
                    if (LocationPermission.granted(CheckInPage.this)) {
                        showMyLocationOnMap();
                    } else if (!shouldShowRequestPermissionRationale(
                            android.Manifest.permission.ACCESS_FINE_LOCATION)) {
                        // "Don't ask again": only the app's settings page can turn it on.
                        snack(getString(R.string.state_permission_blocked_body), R.string.open_settings,
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                Uri.fromParts("package", getPackageName(), null)));
                                    }
                                });
                    }
                }
            });

    /** Android's own permission prompt (fine + coarse, never background). */
    private final ActivityResultLauncher<String[]> askLocation = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), new ActivityResultCallback<Map<String, Boolean>>() {
                @Override
                public void onActivityResult(Map<String, Boolean> result) {
                    if (LocationPermission.granted(CheckInPage.this)) {
                        hideState();
                        startCheckIn();
                    } else {
                        // No rationale after a refusal = "Don't ask again": only Settings can fix it.
                        permissionBlocked = !shouldShowRequestPermissionRationale(
                                android.Manifest.permission.ACCESS_FINE_LOCATION);
                        showState(State.PERMISSION, null);
                    }
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = CheckInPageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        padForSystemBars(binding.checkInRoot);

        sessionId = getIntent().getStringExtra(EXTRA_SESSION);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (sessionId == null || user == null) {
            finish();
            return;
        }
        uid = user.getUid();
        deviceHash = PhoneCheckPage.deviceHash(this);
        fused = LocationServices.getFusedLocationProviderClient(this);

        binding.btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        binding.btnCheckIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCheckIn();
            }
        });
        binding.btnMyLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onShowMyLocation();
            }
        });
        binding.btnDirections.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDirections();
            }
        });
        binding.state.statePrimary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onStatePrimary();
            }
        });
        binding.state.stateSecondary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Server clock: the window and late checks use server time, not the phone's.
        subs.listen(Db.get().getReference(".info/serverTimeOffset"), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snap) {
                Long offset = snap.getValue(Long.class);
                if (offset != null) {
                    serverOffset = offset;
                } else {
                    serverOffset = 0;
                }
            }

            @Override
            public void onError(String message) {
            }
        });
        // Offline state: no check-in without a server connection.
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
                listenToSession(); // The page still works, course names just show IDs
            }
        });
        handler.post(tick);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Back from Android settings: if what was missing is now fixed, return to the page.
        if (shownState == State.GPS_OFF && gpsOn()) {
            hideState();
        }
        if (shownState == State.PERMISSION && LocationPermission.granted(this)) {
            hideState();
        }
        if (venueMap != null) {
            venueMap.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Also switches the blue dot's location updates off while the page is not shown.
        if (venueMap != null) {
            venueMap.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (venueMap != null) {
            venueMap.onDestroyView();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        subs.clear();
        handler.removeCallbacks(tick);
        if (fixCancel != null) {
            fixCancel.cancel();
        }
    }

    /** Live session, so the page knows at once if the lecturer closes it. */
    private void listenToSession() {
        subs.listen(Db.root().child("sessions").child(sessionId), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snap) {
                if (snap.exists()) {
                    session = snap.getValue(Session.class);
                } else {
                    session = null;
                }
                if (session != null) {
                    session.id = sessionId;
                }
                renderSession();
            }

            @Override
            public void onError(String message) {
                renderSession();
            }
        });
    }

    /** The time now on the server. */
    private long serverNow() {
        return System.currentTimeMillis() + serverOffset;
    }

    // ------------------------------------------------------------- the page

    /** Shows the session details. */
    private void renderSession() {
        binding.progressLoad.setVisibility(View.GONE);
        // Already over (closed by the lecturer, or past its end time): show "Session closed"
        // straight away instead of a "Ready" button that can only fail.
        if (session == null || !session.isOpen() || serverNow() >= session.end) {
            showState(State.CLOSED, null);
            return;
        }
        if (shownState == null) {
            binding.scrollMain.setVisibility(View.VISIBLE);
        } else {
            binding.scrollMain.setVisibility(View.GONE);
        }
        if (shownState == null) {
            binding.btnCheckIn.setVisibility(View.VISIBLE);
        } else {
            binding.btnCheckIn.setVisibility(View.GONE);
        }
        binding.txtType.setText(SessionTypes.name(session.type));
        binding.txtTimeRange.setText(Formats.timeRange(session.start, session.end));
        binding.txtTitle.setText(session.title);
        binding.txtCourse.setText(SessionText.course(session));
        binding.txtVenue.setText(SessionText.room(this, session));
        showVenueOnMap(session);
        if (!busy) {
            showReady();
        }
        showCountdown();
    }

    // -------------------------------------------------------------- the map

    /** Pin at the session's lat/lng and the circle with its radius. */
    private void showVenueOnMap(Session s) {
        binding.txtMapPlace.setText(SessionText.room(this, s));
        if (venueMap == null) {
            venueMap = new VenuePinMap(binding.mapVenue, s.lat, s.lng, s.radius);
            venueMap.setEditable(false); // Students cannot drag or tap-move the pin
            return;
        }
        venueMap.setRadius(s.radius);
    }

    /** "Show my location": explain and ask for permission first, then show the blue dot. */
    private void onShowMyLocation() {
        if (venueMap == null) return;
        if (!LocationPermission.granted(this)) {
            LocationPermission.explain(this, R.string.state_permission_body,
                    new Runnable() {
                        @Override
                        public void run() {
                            askMapLocation.launch(LocationPermission.PERMISSIONS);
                        }
                    });
            return;
        }
        showMyLocationOnMap();
    }

    /** Shows the student's blue dot on the map. */
    private void showMyLocationOnMap() {
        if (venueMap == null || !venueMap.showMyLocation()) return;
        if (!gpsOn()) {
            snack(getString(R.string.state_gps_off_body), R.string.open_settings,
                    new Runnable() {
                        @Override
                        public void run() {
                            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        }
                    });
        }
        showingMe = true;
        binding.txtMyDistance.setVisibility(View.VISIBLE);
        venueMap.zoomToVenueAndMe();
        showMyDistance();
    }

    /** "You are about 120 m from the venue", refreshed every second by tick. */
    private void showMyDistance() {
        if (!showingMe || venueMap == null || session == null) return;
        GeoPoint me = venueMap.getMyLocation();
        if (me == null) {
            binding.txtMyDistance.setText(R.string.finding_location);
            return;
        }
        float[] d = new float[1];
        Location.distanceBetween(me.getLatitude(), me.getLongitude(), session.lat, session.lng, d);
        binding.txtMyDistance.setText(getString(R.string.map_my_distance, Math.round(d[0])));
    }

    /** Opens the phone's maps app at the venue: geo:lat,lng?q=lat,lng(place name). */
    private void openDirections() {
        Session s = session;
        if (s == null) return;
        String latLng = String.format(Locale.ROOT, "%.6f,%.6f", s.lat, s.lng);
        Uri uri = Uri.parse("geo:" + latLng + "?q=" + latLng
                + "(" + Uri.encode(SessionText.room(this, s)) + ")");
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            snack(getString(R.string.map_no_maps_app), 0, null);
        }
    }

    /** Snackbar above the Check in button, with an optional action (action 0 = none). */
    private void snack(String message, @StringRes int action, @Nullable Runnable onAction) {
        Snackbar bar = Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG);
        if (binding.btnCheckIn.getVisibility() == View.VISIBLE) {
            bar.setAnchorView(binding.btnCheckIn);
        }
        if (action != 0 && onAction != null) {
            bar.setAction(action, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onAction.run();
                }
            });
        }
        bar.show();
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
            String left;
            if (secs >= 3600) {
                left = String.format(Locale.ROOT, "%d:%02d:%02d", secs / 3600, secs / 60 % 60,
                        secs % 60);
            } else {
                left = String.format(Locale.ROOT, "%d:%02d", secs / 60, secs % 60);
            }
            binding.txtClosesIn.setText(getString(R.string.closes_in, left));
        } else {
            binding.txtClosesIn.setText(R.string.status_closed);
        }
    }

    /** Shows "Ready to check in". */
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

    /** Turns the Check in button on again. */
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

    /** Fills the status card. */
    private void setStatus(@DrawableRes int icon, int iconColor, int bgTint,
                           String title, String body) {
        binding.imgStatus.setImageResource(icon);
        binding.imgStatus.setColorFilter(ContextCompat.getColor(this, iconColor));
        binding.statusIconBg.setBackgroundTintList(ContextCompat.getColorStateList(this, bgTint));
        binding.txtStatusTitle.setText(title);
        binding.txtStatusBody.setText(body);
    }

    // --------------------------------------------------------- the checks

    /** "Check in now" (and every Try again). */
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
        checkInJava.isEligible(s, uid, new Callback<Boolean>() {
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

    /** Already checked in (or marked by the lecturer)? */
    private void checkNotAlreadyRecorded(Session s) {
        checkInJava.existingRecord(s.id, uid, new Callback<AttendanceRecord>() {
            @Override
            public void onSuccess(AttendanceRecord existing) {
                if (existing != null) {
                    notBusy();
                    CheckInResultPage.openExisting(CheckInPage.this, s, existing);
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

    /** True if the phone's location is on. */
    private boolean gpsOn() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        return lm != null && LocationManagerCompat.isLocationEnabled(lm);
    }

    /**
     * 5. ONE fresh fix: high accuracy, never a cached old location (maxUpdateAge 0), and at most 15
     * s.
     */
    @SuppressLint("MissingPermission") // Checked in step 3 just before this
    private void getFreshFix(Session s) {
        showBusy(R.string.check_in_step_location);
        CurrentLocationRequest request = new CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setDurationMillis(FIX_TIMEOUT_MS)
                .setMaxUpdateAgeMillis(0)
                .build();
        fixCancel = new CancellationTokenSource();
        fused.getCurrentLocation(request, fixCancel.getToken())
                .addOnSuccessListener(new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (isFinishing()) return;
                        if (location == null) {
                            notBusy();
                            showState(State.LOW_ACCURACY, null);
                            return;
                        }
                        checkFix(s, location);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.e(TAG, "getCurrentLocation failed", e);
                        if (isFinishing()) return;
                        notBusy();
                        showState(State.LOW_ACCURACY, null);
                    }
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
            checkInJava.logRejected(s, uid, deviceHash, Attempt.REASON_MOCK, lat, lng, null, accuracy,
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
            checkInJava.logRejected(s, uid, deviceHash, Attempt.REASON_OUTSIDE, lat, lng, distance, accuracy,
                    thenShowRejected(s, Attempt.REASON_OUTSIDE, distance, accuracy));
            return;
        }

        writeRecord(s, lat, lng, distance, accuracy);
    }

    /** After logging a rejected attempt, show the Rejected result. */
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

            /** Opens the Rejected result page. */
            private void show() {
                CheckInResultPage.openRejected(CheckInPage.this, s, reason,
                        distance, accuracy);
                finish();
            }
        };
    }

    /** API 31+: isMock(). */
    @SuppressWarnings("deprecation")
    private static boolean isMock(Location fix) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return fix.isMock();
        }
        return fix.isFromMockProvider();
    }

    /** 9. Present if the server time is at or before lateAfter, otherwise late. */
    private void writeRecord(Session s, double lat, double lng, double distance, float accuracy) {
        long now = serverNow();
        Session live;
        if (session != null) {
            live = session;
        } else {
            live = s;
        }
        if (!live.isCheckInOpen(now)) {
            notBusy();
            showState(State.CLOSED, null);
            return;
        }
        String status;
        if (now <= live.lateAfter) {
            status = Attendance.PRESENT;
        } else {
            status = Attendance.LATE;
        }
        showBusy(R.string.check_in_step_saving);
        checkInJava.writeAccepted(live, uid, deviceHash, status, lat, lng, distance, accuracy,
                new Callback<Long>() {
                    @Override
                    public void onSuccess(Long savedAt) {
                        CheckInResultPage.openAccepted(CheckInPage.this, live, status,
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

    /** Replaces the page with a state. */
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
                if (permissionBlocked) {
                    fillState(R.drawable.ic_pin, R.string.state_permission_title,
                            getString(R.string.state_permission_blocked_body),
                            R.string.open_settings);
                } else {
                    fillState(R.drawable.ic_pin, R.string.state_permission_title,
                            getString(R.string.state_permission_body), R.string.allow_location);
                }
                break;
            case GPS_OFF:
                fillState(R.drawable.ic_target, R.string.state_gps_off_title,
                        getString(R.string.state_gps_off_body), R.string.open_settings);
                break;
            case LOW_ACCURACY:
                if (detail != null) {
                    fillState(R.drawable.ic_target, R.string.state_low_accuracy_title,
                            getString(R.string.state_low_accuracy_body, detail),
                                    R.string.try_again);
                } else {
                    fillState(R.drawable.ic_target, R.string.state_low_accuracy_title,
                            getString(R.string.state_no_fix_body), R.string.try_again);
                }
                break;
        }
    }

    /** Fills the state card. */
    private void fillState(@DrawableRes int icon, @StringRes int title, String body,
                           @StringRes int button) {
        binding.state.stateIcon.setImageResource(icon);
        binding.state.stateTitle.setText(title);
        binding.state.stateBody.setText(body);
        binding.state.statePrimary.setText(button);
    }

    /** Hides the state card and shows the page. */
    private void hideState() {
        shownState = null;
        binding.state.getRoot().setVisibility(View.GONE);
        binding.scrollMain.setVisibility(View.VISIBLE);
        binding.btnCheckIn.setVisibility(View.VISIBLE);
        showReady();
    }

    /** What the state card's main button does. */
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
