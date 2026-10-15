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

/** Keeps the Add Teaching Class page's lists and choices across screen rotation. */
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

    /** Fills the form from an existing class (Edit). */
    public void prefill(String courseId, String groupId) {
        course = SeedCache.course(courseId);
        if (course == null) return;
        // Take the part from the group: the course alone can be in more than one part.
        Group group = findGroup(groupId);
        part = group != null ? group.part : course.part;
        for (Programme p : programmes) {
            if (p.code.equals(course.programme)) programme = p;
        }
        groups.add(groupId);
    }

    /** Parts 1..n of the chosen programme. */
    public int partCount() {
        return programme == null ? 0 : programme.parts;
    }

    /**
     * Courses of the chosen programme and part, by code (TBC ones by name). If the ticked groups
     * follow a track, that track's electives go to the top of the list.
     */
    public List<Course> courses() {
        if (programme == null || part == 0) return new ArrayList<>();
        List<Course> list = SeedCache.coursesFor(programme.code, part);
        String track = groupTrack();
        Collections.sort(list, (a, b) -> {
            boolean aFirst = a.inTrack(track, part);
            boolean bFirst = b.inTrack(track, part);
            if (aFirst != bFirst) return aFirst ? -1 : 1;
            return a.label().compareToIgnoreCase(b.label());
        });
        return list;
    }

    /**
     * The track the ticked groups follow, e.g. BIG_DATA for CDCS2306A. Null if no group is ticked,
     * a ticked group has no track, or the groups follow different tracks.
     */
    @Nullable
    public String groupTrack() {
        String track = null;
        for (String id : groups) {
            Group g = findGroup(id);
            if (g == null || g.track == null) return null;
            if (track != null && !track.equals(g.track)) return null;
            track = g.track;
        }
        return track;
    }

    /**
     * Track labels for an elective's chip, e.g. ["Big Data"] (empty for a plain elective). Null
     * when the course isn't an elective at all, so no chip.
     */
    @Nullable
    public List<String> electiveTracks(@Nullable Course c) {
        if (c == null || !c.isElective() || programme == null) return null;
        List<String> labels = new ArrayList<>();
        for (String trackId : new TreeSet<>(c.tracks.keySet())) {
            if (c.inTrack(trackId, part)) labels.add(programme.trackLabel(trackId));
        }
        return labels;
    }

    @Nullable
    private Group findGroup(String id) {
        for (Group g : allGroups) if (g.id.equals(id)) return g;
        return null;
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
