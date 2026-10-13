package com.attendra.uitm.ui.lecturer;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.attendra.uitm.R;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.SeedCache;
import com.attendra.uitm.data.TeachingRepository;
import com.attendra.uitm.databinding.ActivityAddTeachingClassBinding;
import com.attendra.uitm.model.Course;
import com.attendra.uitm.model.Group;
import com.attendra.uitm.model.Programme;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.model.TeachingAssignment;
import com.attendra.uitm.ui.BaseActivity;
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
public class AddTeachingClassActivity extends BaseActivity {

    private static final String EXTRA_MODE = "mode";
    private static final String EXTRA_COURSE = "course";
    private static final String EXTRA_GROUP = "group";
    private static final String EXTRA_SEMESTER = "semester";
    private static final String EXTRA_CREATED = "created";
    private static final int MODE_ADD = 0;
    private static final int MODE_EDIT = 1;
    private static final int MODE_SETUP = 2;

    public static void open(Context from) {
        from.startActivity(new Intent(from, AddTeachingClassActivity.class));
    }

    public static void openToEdit(Context from, TeachingAssignment a) {
        Intent intent = new Intent(from, AddTeachingClassActivity.class)
                .putExtra(EXTRA_MODE, MODE_EDIT)
                .putExtra(EXTRA_COURSE, a.courseId)
                .putExtra(EXTRA_GROUP, a.groupId)
                .putExtra(EXTRA_SEMESTER, a.semester);
        if (a.createdAt != null) intent.putExtra(EXTRA_CREATED, (long) a.createdAt);
        from.startActivity(intent);
    }

    /** First-time setup after the Preferred Name page; clears the back stack. */
    public static Intent setupIntent(Context from) {
        return new Intent(from, AddTeachingClassActivity.class)
                .putExtra(EXTRA_MODE, MODE_SETUP)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }

    private ActivityAddTeachingClassBinding binding;
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

        binding = ActivityAddTeachingClassBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        padForSystemBars(binding.teachRoot);

        boolean setup = mode == MODE_SETUP;
        // Setup: no app bar (nothing to go back to), a big title and "Skip for now".
        binding.appbar.getRoot().setVisibility(setup ? View.GONE : View.VISIBLE);
        binding.appbar.txtAppbarTitle.setText(mode == MODE_EDIT
                ? R.string.teach_edit_title : R.string.teach_add_title);
        binding.appbar.btnBack.setOnClickListener(v -> close());
        binding.txtSetupTitle.setVisibility(setup ? View.VISIBLE : View.GONE);
        binding.btnCancel.setVisibility(setup ? View.GONE : View.VISIBLE);
        binding.btnSkip.setVisibility(setup ? View.VISIBLE : View.GONE);
        binding.btnCancel.setOnClickListener(v -> close());
        binding.btnSkip.setOnClickListener(v -> close());
        binding.btnSave.setOnClickListener(v -> save());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                close();
            }
        });

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
            vm.semester = saved != null ? saved : SemesterUtil.currentSemester();
        }
        vm.load(uid);
        vm.getLoadState().observe(this, state -> {
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

    private void bindForm() {
        bindSemester();
        bindProgramme();
        bindPart();
        bindCourse();
        bindGroups();
    }

    private void bindSemester() {
        List<String> options = new ArrayList<>(SemesterUtil.options());
        if (!options.contains(vm.semester)) options.add(0, vm.semester); // Editing an older label
        DropdownAdapter<String> adapter = new DropdownAdapter<>(this, options, s -> s);
        binding.inputSemester.setAdapter(adapter);
        binding.inputSemester.setText(vm.semester, false);
        binding.inputSemester.setOnItemClickListener((parent, v, position, id) ->
                vm.semester = (String) parent.getItemAtPosition(position));
    }

    private void bindProgramme() {
        DropdownAdapter<Programme> adapter = new DropdownAdapter<>(this, vm.programmes,
                Programme::label);
        binding.inputProgramme.setAdapter(adapter);
        if (vm.programme != null) binding.inputProgramme.setText(adapter.labelOf(vm.programme), false);
        binding.inputProgramme.setOnItemClickListener((parent, v, position, id) -> {
            Programme chosen = (Programme) parent.getItemAtPosition(position);
            if (chosen == vm.programme) return;
            // A new programme means the old part, course and groups no longer apply.
            vm.programme = chosen;
            vm.part = 0;
            vm.course = null;
            vm.groups.clear();
            binding.inputPart.setText("", false);
            bindPart();
            bindCourse();
            bindGroups();
        });
    }

    private void bindPart() {
        List<Integer> parts = new ArrayList<>();
        for (int i = 1; i <= vm.partCount(); i++) parts.add(i);
        DropdownAdapter<Integer> adapter = new DropdownAdapter<>(this, parts,
                p -> getString(R.string.part_n, p));
        binding.inputPart.setAdapter(adapter);
        if (vm.part != 0) binding.inputPart.setText(adapter.labelOf(vm.part), false);
        binding.layoutPart.setEnabled(vm.programme != null);
        binding.inputPart.setOnItemClickListener((parent, v, position, id) -> {
            int chosen = (Integer) parent.getItemAtPosition(position);
            if (chosen == vm.part) return;
            vm.part = chosen;
            vm.course = null;
            vm.groups.clear();
            bindCourse();
            bindGroups();
        });
    }

    private void bindCourse() {
        List<Course> courses = vm.courses();
        // Course.label() shows the name alone when the code is "TBC".
        DropdownAdapter<Course> adapter = new DropdownAdapter<>(this, courses, Course::label);
        binding.inputCourse.setAdapter(adapter);
        binding.inputCourse.setText(vm.course != null ? adapter.labelOf(vm.course) : "", false);
        binding.layoutCourse.setEnabled(vm.programme != null && vm.part != 0);
        showSulamChip();
        binding.inputCourse.setOnItemClickListener((parent, v, position, id) -> {
            vm.course = (Course) parent.getItemAtPosition(position);
            binding.layoutCourse.setError(null);
            binding.layoutCourse.setErrorEnabled(false); // Also drop the space the error used
            showSulamChip();
        });
    }

    /** "SULAM course" chip under the dropdown when the course is the programme's SULAM course. */
    private void showSulamChip() {
        boolean sulam = vm.course != null && SeedCache.isSulamCourse(vm.course.id);
        binding.chipSulam.setVisibility(sulam ? View.VISIBLE : View.GONE);
    }

    private void bindGroups() {
        binding.listGroups.removeAllViews();
        List<Group> groups = vm.groupOptions();
        if (groups.isEmpty()) {
            TextView hint = new TextView(this);
            hint.setText(vm.part == 0 ? R.string.teach_groups_pick_part : R.string.teach_groups_none);
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
            box.setOnCheckedChangeListener((b, checked) -> {
                if (checked) vm.groups.add(g.id); else vm.groups.remove(g.id);
                binding.txtGroupsError.setVisibility(View.GONE);
            });
            binding.listGroups.addView(box);
        }
    }

    // ------------------------------------------------------------ saving

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
        TeachingRepository.save(uid, vm.semester, vm.course.id, toSave, oldKey, oldCreatedAt,
                new Callback<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        if (anySkipped) {
                            Toast.makeText(AddTeachingClassActivity.this,
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
