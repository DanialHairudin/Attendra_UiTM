package com.attendra.uitm.ui.lecturer;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.Db;
import com.attendra.uitm.data.SeedCache;
import com.attendra.uitm.data.SeedRepository;
import com.attendra.uitm.data.TeachingRepository;
import com.attendra.uitm.model.Course;
import com.attendra.uitm.model.Group;
import com.attendra.uitm.model.Programme;
import com.attendra.uitm.model.TeachingAssignment;
import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Keeps the Add Teaching Class page's lists and choices across screen rotation.
 * Loads once: programmes and groups (seed), courses (SeedCache) and the keys
 * the lecturer already has (to skip classes already in the list).
 */
public class AddTeachingClassViewModel extends ViewModel {

    public enum LoadState { LOADING, READY, ERROR }

    private final MutableLiveData<LoadState> loadState = new MutableLiveData<>(LoadState.LOADING);
    private String loadError;
    private boolean started;

    public final List<Programme> programmes = new ArrayList<>();
    private final List<Group> allGroups = new ArrayList<>();
    /** Keys already in teachingAssignments/{uid}, e.g. "CS240_ICT600_CDCS2405A". */
    public final Set<String> existingKeys = new HashSet<>();

    // ------------------------------------------------------------ the form
    public String semester;
    @Nullable
    public Programme programme;
    public int part;
    @Nullable
    public Course course;
    public final TreeSet<String> groups = new TreeSet<>();

    public LiveData<LoadState> getLoadState() {
        return loadState;
    }

    public String getLoadError() {
        return loadError;
    }

    public void load(String uid) {
        if (started) return;
        started = true;
        new SeedRepository().loadProgrammes(new Callback<List<Programme>>() {
            @Override
            public void onSuccess(List<Programme> list) {
                programmes.addAll(list);
                Collections.sort(programmes, (a, b) -> a.code.compareTo(b.code));
                loadGroups(uid);
            }

            @Override
            public void onError(String message) {
                fail(message);
            }
        });
    }

    private void loadGroups(String uid) {
        new SeedRepository().loadGroups(new Callback<List<Group>>() {
            @Override
            public void onSuccess(List<Group> list) {
                allGroups.addAll(list);
                Collections.sort(allGroups, (a, b) -> a.id.compareTo(b.id));
                loadCourses(uid);
            }

            @Override
            public void onError(String message) {
                fail(message);
            }
        });
    }

    private void loadCourses(String uid) {
        SeedCache.load(new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                loadExisting(uid);
            }

            @Override
            public void onError(String message) {
                fail(message);
            }
        });
    }

    private void loadExisting(String uid) {
        Db.readOnce(TeachingRepository.ref(uid), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                for (TeachingAssignment a : TeachingRepository.parse(snapshot)) {
                    existingKeys.add(a.key());
                }
                loadState.setValue(LoadState.READY);
            }

            @Override
            public void onError(String message) {
                fail(message);
            }
        });
    }

    private void fail(String message) {
        loadError = message;
        loadState.setValue(LoadState.ERROR);
    }

    /** Fills the form from an existing class (Edit). Call once, after loading. */
    public void prefill(String courseId, String groupId) {
        course = SeedCache.course(courseId);
        if (course == null) return;
        part = course.part;
        for (Programme p : programmes) {
            if (p.code.equals(course.programme)) programme = p;
        }
        groups.add(groupId);
    }

    /** Parts 1..n of the chosen programme. */
    public int partCount() {
        return programme == null ? 0 : programme.parts;
    }

    /** Courses of the chosen programme and part, by code (TBC ones by name). */
    public List<Course> courses() {
        if (programme == null || part == 0) return new ArrayList<>();
        List<Course> list = SeedCache.coursesFor(programme.code, part);
        Collections.sort(list, (a, b) -> a.label().compareToIgnoreCase(b.label()));
        return list;
    }

    /** Groups of the chosen programme and part, e.g. CDCS2405A, B, C. */
    public List<Group> groupOptions() {
        List<Group> list = new ArrayList<>();
        if (programme == null || part == 0) return list;
        for (Group g : allGroups) {
            if (programme.code.equals(g.programme) && g.part == part) list.add(g);
        }
        return list;
    }
}
