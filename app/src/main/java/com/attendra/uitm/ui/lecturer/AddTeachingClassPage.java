package com.attendra.uitm.ui.lecturer;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.attendra.uitm.R;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.SeedCache;
import com.attendra.uitm.data.TeachingJava;
import com.attendra.uitm.databinding.AddTeachingClassPageBinding;
import com.attendra.uitm.model.Course;
import com.attendra.uitm.model.Group;
import com.attendra.uitm.model.Programme;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.model.TeachingAssignment;
import com.attendra.uitm.ui.BasePage;
import com.attendra.uitm.ui.common.Ui;
import com.attendra.uitm.util.DropdownAdapter;
import com.attendra.uitm.util.HomeRouter;
import com.attendra.uitm.util.SemesterUtil;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

/** Add Teaching Class Page. */
public class AddTeachingClassPage extends BasePage {

    private static final String EXTRA_MODE = "mode";
    private static final String EXTRA_COURSE = "course";
    private static final String EXTRA_GROUP = "group";
    private static final String EXTRA_SEMESTER = "semester";
    private static final String EXTRA_CREATED = "created";
    private static final int MODE_ADD = 0;
    private static final int MODE_EDIT = 1;
    private static final int MODE_SETUP = 2;

    /** Opens the page to add a class. */
    public static void open(Context from) {
        from.startActivity(new Intent(from, AddTeachingClassPage.class));
    }

    /** Opens the page to change a class. */
    public static void openToEdit(Context from, TeachingAssignment a) {
        Intent intent = new Intent(from, AddTeachingClassPage.class)
                .putExtra(EXTRA_MODE, MODE_EDIT)
                .putExtra(EXTRA_COURSE, a.courseId)
                .putExtra(EXTRA_GROUP, a.groupId)
                .putExtra(EXTRA_SEMESTER, a.semester);
        if (a.createdAt != null) {
            intent.putExtra(EXTRA_CREATED, (long) a.createdAt);
        }
        from.startActivity(intent);
    }

    /** First-time setup after the Preferred Name page; clears the back stack. */
    public static Intent setupIntent(Context from) {
        return new Intent(from, AddTeachingClassPage.class)
                .putExtra(EXTRA_MODE, MODE_SETUP)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }

    private AddTeachingClassPageBinding binding;
    private AddTeachingClassViewModel vm;
    private String uid;
    private int mode;
    /** Edit only: the class being changed. */
    private String oldKey;
    private Long oldCreatedAt;
    private boolean saving;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            HomeRouter.openSplash(this);
            return;
        }
        uid = user.getUid();
        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_ADD);

        binding = AddTeachingClassPageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        padForSystemBars(binding.teachRoot);

        boolean setup = mode == MODE_SETUP;
        showTitle(setup);
        setUpButtons(setup);

        vm = new ViewModelProvider(this).get(AddTeachingClassViewModel.class);
        if (mode == MODE_EDIT) {
            String course = getIntent().getStringExtra(EXTRA_COURSE);
            String group = getIntent().getStringExtra(EXTRA_GROUP);
            oldKey = TeachingAssignment.key(course, group);
            if (getIntent().hasExtra(EXTRA_CREATED)) {
                oldCreatedAt = getIntent().getLongExtra(EXTRA_CREATED, 0);
            }
        }
        boolean firstLoad = vm.getLoadState().getValue() != AddTeachingClassViewModel.LoadState.READY
                && vm.semester == null;
        if (firstLoad) {
            String saved = getIntent().getStringExtra(EXTRA_SEMESTER);
            if (saved != null) {
                vm.semester = saved;
            } else {
                vm.semester = SemesterUtil.currentSemester();
            }
        }
        vm.load(uid);
        watchLoadState();
    }

    /** Title bar for add / edit, big title for first-time set up. */
    private void showTitle(boolean setup) {
        // Setup: no app bar (nothing to go back to), a big title and "Skip for now".
        if (setup) {
            binding.appbar.getRoot().setVisibility(View.GONE);
        } else {
            binding.appbar.getRoot().setVisibility(View.VISIBLE);
        }
        if (mode == MODE_EDIT) {
            binding.appbar.txtAppbarTitle.setText(R.string.teach_edit_title);
        } else {
            binding.appbar.txtAppbarTitle.setText(R.string.teach_add_title);
        }
    }

    /** What each button (and the Back button) does. */
    private void setUpButtons(boolean setup) {
        binding.appbar.btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                close();
            }
        });
        if (setup) {
            binding.txtSetupTitle.setVisibility(View.VISIBLE);
            binding.btnCancel.setVisibility(View.GONE);
            binding.btnSkip.setVisibility(View.VISIBLE);
        } else {
            binding.txtSetupTitle.setVisibility(View.GONE);
            binding.btnCancel.setVisibility(View.VISIBLE);
            binding.btnSkip.setVisibility(View.GONE);
        }
        binding.btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                close();
            }
        });
        binding.btnSkip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                close();
            }
        });
        binding.btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                close();
            }
        });
    }

    /** Shows the form once the data is loaded. */
    private void watchLoadState() {
        vm.getLoadState().observe(this, new Observer<AddTeachingClassViewModel.LoadState>() {
            @Override
            public void onChanged(AddTeachingClassViewModel.LoadState state) {
                if (state == AddTeachingClassViewModel.LoadState.ERROR) {
                    binding.txtTeachLoading.setText(vm.getLoadError());
                    return;
                }
                if (state != AddTeachingClassViewModel.LoadState.READY) return;
                if (mode == MODE_EDIT && vm.course == null && vm.groups.isEmpty()) {
                    vm.prefill(getIntent().getStringExtra(EXTRA_COURSE),
                            getIntent().getStringExtra(EXTRA_GROUP));
                }
                binding.txtTeachLoading.setVisibility(View.GONE);
                binding.groupForm.setVisibility(View.VISIBLE);
                bindForm();
            }
        });
    }

    /** Cancel / back / Skip for now. */
    private void close() {
        if (mode == MODE_SETUP) {
            HomeRouter.openHome(this, Registration.ROLE_LECTURER);
        } else {
            finish();
        }
    }

    // ------------------------------------------------------------ the form

    /** Fills every field. */
    private void bindForm() {
        bindSemester();
        bindProgramme();
        bindPart();
        bindGroups();
        bindCourse();
    }

    /** Fills the Semester dropdown. */
    private void bindSemester() {
        List<String> options = new ArrayList<>(SemesterUtil.options());
        if (!options.contains(vm.semester)) {
            options.add(0, vm.semester); // Editing an older label
        }
        binding.inputSemester.setAdapter(new DropdownAdapter(this, options));
        binding.inputSemester.setText(vm.semester, false);
        binding.inputSemester.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                vm.semester = options.get(position);
            }
        });
    }

    /** Fills the Programme dropdown. */
    private void bindProgramme() {
        List<Programme> programmes = new ArrayList<>(vm.programmes);
        List<String> labels = new ArrayList<>();
        for (Programme p : programmes) {
            labels.add(p.label());
        }
        binding.inputProgramme.setAdapter(new DropdownAdapter(this, labels));
        if (vm.programme != null) {
            binding.inputProgramme.setText(vm.programme.label(), false);
        }
        binding.inputProgramme.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                Programme chosen = programmes.get(position);
                if (chosen == vm.programme) return;
                // A new programme means the old part, course and groups no longer apply.
                vm.programme = chosen;
                vm.part = 0;
                vm.course = null;
                vm.groups.clear();
                binding.inputPart.setText("", false);
                bindPart();
                bindGroups();
                bindCourse();
            }
        });
    }

    /** Fills the Part dropdown. */
    private void bindPart() {
        List<Integer> parts = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int i = 1; i <= vm.partCount(); i++) {
            parts.add(i);
            labels.add(getString(R.string.part_n, i));
        }
        binding.inputPart.setAdapter(new DropdownAdapter(this, labels));
        if (vm.part != 0) {
            binding.inputPart.setText(getString(R.string.part_n, vm.part), false);
        }
        binding.layoutPart.setEnabled(vm.programme != null);
        binding.inputPart.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                int chosen = parts.get(position);
                if (chosen == vm.part) return;
                vm.part = chosen;
                vm.course = null;
                vm.groups.clear();
                bindGroups();
                bindCourse();
            }
        });
    }

    /** Fills the Course dropdown. */
    private void bindCourse() {
        List<Course> courses = vm.courses();
        // Course.label() shows the name alone when the code is "TBC".
        List<String> labels = new ArrayList<>();
        for (Course c : courses) {
            labels.add(c.label());
        }
        binding.inputCourse.setAdapter(new DropdownAdapter(this, labels));
        if (vm.course != null) {
            binding.inputCourse.setText(vm.course.label(), false);
        } else {
            binding.inputCourse.setText("", false);
        }
        binding.layoutCourse.setEnabled(vm.programme != null && vm.part != 0);
        showChips();
        binding.inputCourse.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                vm.course = courses.get(position);
                binding.layoutCourse.setError(null);
                binding.layoutCourse.setErrorEnabled(false); // Also drop the space the error used
                showChips();
            }
        });
    }

    /**
     * Chips under the dropdown: "Elective" (with the track, e.g. "Elective · Big Data") for an
     * elective, and "SULAM course" for the programme's SULAM course.
     */
    private void showChips() {
        List<String> tracks = vm.electiveTracks(vm.course);
        if (tracks == null) {
            binding.chipElective.setVisibility(View.GONE);
        } else {
            if (tracks.isEmpty()) {
                binding.chipElective.setText(getString(R.string.elective_chip));
            } else {
                binding.chipElective.setText(getString(R.string.elective_chip_track,
                        TextUtils.join(" / ", tracks)));
            }
            binding.chipElective.setVisibility(View.VISIBLE);
        }
        boolean sulam = vm.course != null && SeedCache.isSulamCourse(vm.course.id);
        if (sulam) {
            binding.chipSulam.setVisibility(View.VISIBLE);
        } else {
            binding.chipSulam.setVisibility(View.GONE);
        }
    }

    /** Shows one chip per group. */
    private void bindGroups() {
        binding.listGroups.removeAllViews();
        List<Group> groups = vm.groupOptions();
        if (groups.isEmpty()) {
            TextView hint = new TextView(this);
            if (vm.part == 0) {
                hint.setText(R.string.teach_groups_pick_part);
            } else {
                hint.setText(R.string.teach_groups_none);
            }
            hint.setTextAppearance(R.style.TextAppearance_Attendra_Caption);
            int pad = Ui.dp(this, 12);
            hint.setPadding(pad, pad, pad, pad);
            binding.listGroups.addView(hint);
            return;
        }
        for (Group g : groups) {
            MaterialCheckBox box = new MaterialCheckBox(this);
            box.setText(g.id);
            box.setMinHeight(Ui.dp(this, 48));
            box.setSaveEnabled(false); // The ViewModel keeps the ticks across rotation
            box.setChecked(vm.groups.contains(g.id));
            box.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton b, boolean checked) {
                    String trackBefore = vm.groupTrack();
                    if (checked) {
                        vm.groups.add(g.id);
                    } else {
                        vm.groups.remove(g.id);
                    }
                    binding.txtGroupsError.setVisibility(View.GONE);
                    // A group with a track (e.g. CDCS2306A, Big Data) puts that track's electives first.
                    if (!TextUtils.equals(trackBefore, vm.groupTrack())) {
                        bindCourse();
                    }
                }
            });
            binding.listGroups.addView(box);
        }
    }

    // ------------------------------------------------------------ saving

    /** Checks the form, then saves the classes. */
    private void save() {
        if (saving) return;
        boolean ok = true;
        if (vm.course == null) {
            binding.layoutCourse.setError(getString(R.string.teach_error_course));
            ok = false;
        }
        if (vm.groups.isEmpty()) {
            binding.txtGroupsError.setText(R.string.teach_error_groups);
            binding.txtGroupsError.setVisibility(View.VISIBLE);
            ok = false;
        }
        if (!ok) return;

        // Skip classes already in the list (the class being edited doesn't count).
        List<String> toSave = new ArrayList<>();
        boolean skipped = false;
        for (String groupId : vm.groups) {
            String key = TeachingAssignment.key(vm.course.id, groupId);
            if (vm.existingKeys.contains(key) && !key.equals(oldKey)) {
                skipped = true;
            } else {
                toSave.add(groupId);
            }
        }
        if (toSave.isEmpty()) {
            Snackbar.make(binding.getRoot(), R.string.teach_already_listed, Snackbar.LENGTH_LONG).show();
            return;
        }

        saving = true;
        binding.btnSave.setEnabled(false);
        final boolean anySkipped = skipped;
        TeachingJava.save(uid, vm.semester, vm.course.id, toSave, oldKey, oldCreatedAt,
                new Callback<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        if (anySkipped) {
                            Toast.makeText(AddTeachingClassPage.this,
                                    R.string.teach_already_listed, Toast.LENGTH_LONG).show();
                        }
                        close();
                    }

                    @Override
                    public void onError(String message) {
                        saving = false;
                        binding.btnSave.setEnabled(true);
                        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
                    }
                });
    }
}
