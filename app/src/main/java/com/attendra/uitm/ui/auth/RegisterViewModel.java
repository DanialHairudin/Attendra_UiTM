package com.attendra.uitm.ui.auth;


import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.attendra.uitm.data.AuthJava;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.SeedJava;
import com.attendra.uitm.model.Club;
import com.attendra.uitm.model.Group;
import com.attendra.uitm.model.Programme;
import com.attendra.uitm.model.Registration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps the Register screen's data alive when the screen rotates: the seed lists from the database
 * and the user's choices (role, programme, part, group, clubs).
 */
public class RegisterViewModel extends ViewModel {

    /** Seed loading state for the screen to observe. */
    public enum SeedState { LOADING, READY, ERROR }

    private final SeedJava seed = new SeedJava();

    private final MutableLiveData<SeedState> seedState = new MutableLiveData<>();
    private String seedError;

    public final List<Programme> programmes = new ArrayList<>();
    public final List<Group> allGroups = new ArrayList<>();
    public final List<Club> clubs = new ArrayList<>();

    // Current choices
    public String role = Registration.ROLE_STUDENT;
    public Programme programme;
    public int part; // 0 = not chosen
    public String groupId;
    /** The track the student picked (only when the group doesn't set one). */
    public String trackId;
    /** Ordered set so the saved clubs keep the order they appear on screen. */
    public final Set<String> selectedClubs = new LinkedHashSet<>();

    /** True while the account is being created; blocks a second tap on "Create account". */
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    /** Set once, when the account is created: the email to show on the Verify screen. */
    private final MutableLiveData<String> registeredEmail = new MutableLiveData<>();
    /** Latest register error. */
    private final MutableLiveData<String> registerError = new MutableLiveData<>();

    private final AuthJava auth = new AuthJava();

    /** Runs the register flow here instead of in the Fragment. */
    public void register(Registration r) {
        if (Boolean.TRUE.equals(submitting.getValue())) return;
        submitting.setValue(true);
        auth.register(r, new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                submitting.setValue(false);
                registeredEmail.setValue(r.email);
            }

            @Override
            public void onError(String message) {
                submitting.setValue(false);
                registerError.setValue(message);
            }
        });
    }

    /** True while the account is being created. */
    public LiveData<Boolean> getSubmitting() {
        return submitting;
    }

    /** The email once the account is created. */
    public LiveData<String> getRegisteredEmail() {
        return registeredEmail;
    }

    /** The sign-up error to show, or null. */
    public LiveData<String> getRegisterError() {
        return registerError;
    }

    /** Forgets the error after it was shown. */
    public void clearRegisterError() {
        registerError.setValue(null);
    }

    /** Loading, ready or error, for programmes and clubs. */
    public LiveData<SeedState> getSeedState() {
        return seedState;
    }

    /** Why the data did not load. */
    public String getSeedError() {
        return seedError;
    }

    /** Loads programmes, groups and clubs once. */
    public void loadSeedIfNeeded() {
        SeedState current = seedState.getValue();
        if (current == SeedState.LOADING || current == SeedState.READY) return;
        seedState.setValue(SeedState.LOADING);

        // The three reads run one after another.
        seed.loadProgrammes(new Callback<List<Programme>>() {
            @Override
            public void onSuccess(List<Programme> result) {
                programmes.clear();
                programmes.addAll(result);
                seed.loadGroups(new Callback<List<Group>>() {
                    @Override
                    public void onSuccess(List<Group> result) {
                        allGroups.clear();
                        allGroups.addAll(result);
                        seed.loadClubs(new Callback<List<Club>>() {
                            @Override
                            public void onSuccess(List<Club> result) {
                                clubs.clear();
                                clubs.addAll(result);
                                seedState.setValue(SeedState.READY);
                            }

                            @Override
                            public void onError(String message) {
                                fail(message);
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {
                        fail(message);
                    }
                });
            }

            @Override
            public void onError(String message) {
                fail(message);
            }
        });
    }

    /** Remembers why loading failed. */
    private void fail(String message) {
        seedError = message;
        seedState.setValue(SeedState.ERROR);
    }

    /** Parts 1..N for the chosen programme (N comes from /programmes/{code}/parts). */
    public int partCount() {
        if (programme == null) {
            return 0;
        }
        return programme.parts;
    }

    /** True when the chosen programme splits into tracks at the chosen part. */
    public boolean needsTrack() {
        return programme != null && programme.hasTracksIn(part);
    }

    /** The track the chosen group follows, e.g. "BIG_DATA", or null if the student picks. */
    public String groupTrack() {
        for (Group g : allGroups) {
            if (g.id.equals(groupId)) return g.track;
        }
        return null;
    }

    /** Track IDs of the chosen programme, sorted (e.g. BIG_DATA, CREATIVE_IT, IT_INFRASTRUCTURE). */
    public List<String> trackIds() {
        List<String> ids = new ArrayList<>();
        if (programme != null && programme.tracks != null) {
            ids.addAll(programme.tracks.keySet());
        }
        java.util.Collections.sort(ids);
        return ids;
    }

    /** Groups for the chosen programme and part, e.g. CDCS2405A, CDCS2405B, CDCS2405C. */
    public List<Group> groupsForSelection() {
        List<Group> result = new ArrayList<>();
        if (programme == null || part == 0) return result;
        for (Group g : allGroups) {
            if (programme.code.equals(g.programme) && g.part == part) {
                result.add(g);
            }
        }
        return result;
    }
}
