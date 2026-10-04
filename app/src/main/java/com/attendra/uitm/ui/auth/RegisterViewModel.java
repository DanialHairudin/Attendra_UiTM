package com.attendra.uitm.ui.auth;


import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.attendra.uitm.data.AuthRepository;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.SeedRepository;
import com.attendra.uitm.model.Club;
import com.attendra.uitm.model.Group;
import com.attendra.uitm.model.Programme;
import com.attendra.uitm.model.Registration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps the Register screen's data alive when the screen rotates: the seed lists
 * from the database and the user's choices (role, programme, part, group, clubs).
 * Text fields don't need to be here; Android restores typed text by itself.
 */
public class RegisterViewModel extends ViewModel {

    /** Seed loading state for the screen to observe. */
    public enum SeedState { LOADING, READY, ERROR }

    private final SeedRepository seed = new SeedRepository();

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
    /** Ordered set so the saved clubs keep the order they appear on screen. */
    public final Set<String> selectedClubs = new LinkedHashSet<>();

    /** True while the account is being created; blocks a second tap on "Create account". */
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    /** Set once, when the account is created: the email to show on the Verify screen. */
    private final MutableLiveData<String> registeredEmail = new MutableLiveData<>();
    /** Latest register error. Cleared after the screen has shown it. */
    private final MutableLiveData<String> registerError = new MutableLiveData<>();

    private final AuthRepository auth = new AuthRepository();

    /**
     * Runs the register flow here instead of in the Fragment. The ViewModel
     * survives rotation, so the result still arrives if the screen rotates while
     * "Creating account…" is showing.
     */
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

    public LiveData<Boolean> getSubmitting() {
        return submitting;
    }

    public LiveData<String> getRegisteredEmail() {
        return registeredEmail;
    }

    public LiveData<String> getRegisterError() {
        return registerError;
    }

    public void clearRegisterError() {
        registerError.setValue(null);
    }

    public LiveData<SeedState> getSeedState() {
        return seedState;
    }

    public String getSeedError() {
        return seedError;
    }

    /** Loads programmes, groups and clubs once. Safe to call again after an error. */
    public void loadSeedIfNeeded() {
        SeedState current = seedState.getValue();
        if (current == SeedState.LOADING || current == SeedState.READY) return;
        seedState.setValue(SeedState.LOADING);

        // The three reads run one after another. That keeps the code simple, and
        // the data is small.
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

    private void fail(String message) {
        seedError = message;
        seedState.setValue(SeedState.ERROR);
    }

    /** Parts 1..N for the chosen programme (N comes from /programmes/{code}/parts). */
    public int partCount() {
        return programme == null ? 0 : programme.parts;
    }

    /** Groups for the chosen programme and part, e.g. CS2405A, CS2405B, CS2405C. */
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
