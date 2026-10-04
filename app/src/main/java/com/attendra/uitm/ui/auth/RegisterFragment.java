package com.attendra.uitm.ui.auth;

import static com.attendra.uitm.ui.auth.LoginFragment.raw;
import static com.attendra.uitm.ui.auth.LoginFragment.text;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.attendra.uitm.BuildConfig;
import com.attendra.uitm.R;
import com.attendra.uitm.databinding.FragmentRegisterBinding;
import com.attendra.uitm.model.Club;
import com.attendra.uitm.model.Group;
import com.attendra.uitm.model.Programme;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.util.DropdownAdapter;
import com.attendra.uitm.util.Validators;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Register screen (CLAUDE.md section 4). One form with a Student | Lecturer toggle.
 *
 * Student: student ID, programme → part → group cascade, clubs they join.
 * Lecturer: staff ID, clubs they advise.
 * Both: name, email, phone, password + confirm.
 *
 * Programmes, groups and clubs come from the seed nodes in the database.
 */
public class RegisterFragment extends Fragment {

    private FragmentRegisterBinding binding;
    private RegisterViewModel vm;
    private Snackbar seedSnackbar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRegisterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(this).get(RegisterViewModel.class);

        binding.appbar.txtAppbarTitle.setText(R.string.register_title);
        binding.appbar.btnBack.setOnClickListener(
                v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());

        binding.btnRoleStudent.setOnClickListener(v -> setRole(Registration.ROLE_STUDENT));
        binding.btnRoleLecturer.setOnClickListener(v -> setRole(Registration.ROLE_LECTURER));
        setRole(vm.role);

        binding.btnCreate.setOnClickListener(v -> submit());

        vm.getSubmitting().observe(getViewLifecycleOwner(), loading -> {
            binding.btnCreate.setEnabled(!loading);
            binding.btnCreate.setText(loading ? R.string.creating_account : R.string.create_account_button);
        });
        vm.getRegisteredEmail().observe(getViewLifecycleOwner(), email -> {
            if (email != null) ((AuthActivity) requireActivity()).showVerify(email, true);
        });
        vm.getRegisterError().observe(getViewLifecycleOwner(), message -> {
            if (message == null) return;
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            vm.clearRegisterError(); // show it once, not again after rotation
        });

        vm.getSeedState().observe(getViewLifecycleOwner(), this::onSeedState);
        vm.loadSeedIfNeeded();
    }

    // ---------------------------------------------------------------- role toggle

    /** Shows the fields for one role and hides the other role's fields. */
    private void setRole(String role) {
        vm.role = role;
        boolean student = Registration.ROLE_STUDENT.equals(role);

        binding.btnRoleStudent.setChecked(student);
        binding.btnRoleLecturer.setChecked(!student);

        binding.groupStudentId.setVisibility(student ? View.VISIBLE : View.GONE);
        binding.groupAcademic.setVisibility(student ? View.VISIBLE : View.GONE);
        binding.groupStaffId.setVisibility(student ? View.GONE : View.VISIBLE);

        binding.txtLabelClubs.setText(student
                ? R.string.label_clubs_student : R.string.label_clubs_lecturer);

        // The UiTM-specific label and example only make sense when the UiTM
        // domain is actually required (release builds).
        if (BuildConfig.REQUIRE_UITM_EMAIL) {
            binding.txtLabelEmail.setText(student ? R.string.label_email_student : R.string.label_email_staff);
            binding.inputEmail.setHint(student ? R.string.hint_email_student : R.string.hint_email_staff);
        }

        // Errors on fields that just got hidden would be confusing later.
        binding.layoutStudentId.setError(null);
        binding.layoutStaffId.setError(null);
        binding.layoutEmail.setError(null);
    }

    // ---------------------------------------------------------------- seed data

    private void onSeedState(RegisterViewModel.SeedState state) {
        if (seedSnackbar != null) {
            seedSnackbar.dismiss();
            seedSnackbar = null;
        }
        switch (state) {
            case LOADING:
                binding.txtClubsNote.setText(R.string.clubs_loading);
                binding.layoutProgramme.setEnabled(false);
                updateCascadeEnabled();
                break;
            case READY:
                binding.txtClubsNote.setText(R.string.clubs_optional_note);
                binding.layoutProgramme.setEnabled(true);
                if (vm.programmes.isEmpty()) {
                    binding.layoutProgramme.setError(getString(R.string.error_seed_empty));
                }
                bindProgrammeDropdown();
                bindPartDropdown();
                bindGroupDropdown();
                bindClubChips();
                break;
            case ERROR:
                binding.txtClubsNote.setText(R.string.clubs_optional_note);
                seedSnackbar = Snackbar.make(binding.getRoot(),
                                getString(R.string.error_seed_load, vm.getSeedError()),
                                Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.retry, v -> vm.loadSeedIfNeeded());
                seedSnackbar.show();
                break;
        }
    }

    private void bindProgrammeDropdown() {
        List<String> labels = new ArrayList<>();
        for (Programme p : vm.programmes) labels.add(p.label());
        binding.inputProgramme.setAdapter(new DropdownAdapter(requireContext(), labels));
        binding.inputProgramme.setOnItemClickListener((parent, v, position, id) -> {
            Programme chosen = vm.programmes.get(position);
            if (chosen == vm.programme) return;
            // A new programme means the old part and group no longer apply.
            vm.programme = chosen;
            vm.part = 0;
            vm.groupId = null;
            binding.inputPart.setText("", false);
            binding.inputGroup.setText("", false);
            binding.layoutProgramme.setError(null);
            bindPartDropdown();
            bindGroupDropdown();
        });
    }

    private void bindPartDropdown() {
        List<String> labels = new ArrayList<>();
        for (int i = 1; i <= vm.partCount(); i++) labels.add(getString(R.string.part_n, i));
        binding.inputPart.setAdapter(new DropdownAdapter(requireContext(), labels));
        binding.inputPart.setOnItemClickListener((parent, v, position, id) -> {
            int chosen = position + 1;
            if (chosen == vm.part) return;
            vm.part = chosen;
            vm.groupId = null;
            binding.inputGroup.setText("", false);
            binding.layoutPart.setError(null);
            bindGroupDropdown();
        });
        updateCascadeEnabled();
    }

    private void bindGroupDropdown() {
        List<Group> groups = vm.groupsForSelection();
        List<String> labels = new ArrayList<>();
        for (Group g : groups) labels.add(g.id);
        binding.inputGroup.setAdapter(new DropdownAdapter(requireContext(), labels));
        binding.inputGroup.setOnItemClickListener((parent, v, position, id) -> {
            vm.groupId = groups.get(position).id;
            binding.layoutGroup.setError(null);
        });
        if (vm.programme != null && vm.part != 0 && groups.isEmpty()) {
            binding.layoutGroup.setError(getString(R.string.error_no_groups));
        }
        updateCascadeEnabled();
    }

    /** Part unlocks after a programme is chosen; Group unlocks after a part is chosen. */
    private void updateCascadeEnabled() {
        binding.layoutPart.setEnabled(vm.programme != null);
        binding.layoutGroup.setEnabled(vm.programme != null && vm.part != 0);
    }

    /** One pill per club. Pills are one line and scroll sideways (CLAUDE.md section 3). */
    private void bindClubChips() {
        binding.chipsClubs.removeAllViews();
        for (Club club : vm.clubs) {
            Chip chip = new Chip(requireContext()); // picks up Widget.Attendra.Pill from the theme
            chip.setText(club.shortName);
            chip.setContentDescription(club.name);
            chip.setCheckable(true);
            chip.setChecked(vm.selectedClubs.contains(club.id));
            // The ViewModel remembers the choices, so the chip doesn't need to save its own state.
            chip.setSaveEnabled(false);
            chip.setOnCheckedChangeListener((button, checked) -> {
                if (checked) vm.selectedClubs.add(club.id);
                else vm.selectedClubs.remove(club.id);
            });
            binding.chipsClubs.addView(chip);
        }
    }

    // ---------------------------------------------------------------- submit

    private void submit() {
        Registration r = new Registration();
        r.role = vm.role;
        r.name = text(binding.inputName.getText()).replaceAll("\\s+", " ");
        r.email = text(binding.inputEmail.getText()).toLowerCase();
        String phoneInput = text(binding.inputPhone.getText());
        r.phone = "+60" + Validators.phoneDigits(phoneInput);
        r.password = raw(binding.inputPassword.getText());
        String confirm = raw(binding.inputConfirm.getText());
        r.clubIds.addAll(vm.selectedClubs);

        // Each check puts its message under the field (or clears it when OK).
        // The first field with a problem gets focus.
        TextInputLayout firstError = null;
        firstError = check(binding.layoutName, Validators.name(r.name), firstError);
        if (r.isStudent()) {
            r.studentId = text(binding.inputStudentId.getText());
            firstError = check(binding.layoutStudentId, Validators.studentId(r.studentId), firstError);
        } else {
            r.staffId = text(binding.inputStaffId.getText()).toUpperCase();
            firstError = check(binding.layoutStaffId, Validators.staffId(r.staffId), firstError);
        }
        firstError = check(binding.layoutEmail, Validators.registerEmail(r.email, r.role), firstError);
        firstError = check(binding.layoutPhone, Validators.phone(phoneInput), firstError);
        if (r.isStudent()) {
            firstError = check(binding.layoutProgramme,
                    vm.programme == null ? getString(R.string.error_choose_programme) : null, firstError);
            firstError = check(binding.layoutPart,
                    vm.part == 0 ? getString(R.string.error_choose_part) : null, firstError);
            firstError = check(binding.layoutGroup,
                    vm.groupId == null ? getString(R.string.error_choose_group) : null, firstError);
            if (vm.programme != null) {
                r.programme = vm.programme.code;
                r.part = vm.part;
                r.groupId = vm.groupId;
            }
        }
        firstError = check(binding.layoutPassword, Validators.password(r.password), firstError);
        firstError = check(binding.layoutConfirm, Validators.confirmPassword(r.password, confirm), firstError);

        if (firstError != null) {
            TextInputLayout target = firstError;
            target.requestFocus();
            binding.scroll.post(() -> {
                if (binding != null) {
                    binding.scroll.smoothScrollTo(0, Math.max(0, topInScroll(target) - 120));
                }
            });
            Snackbar.make(binding.getRoot(), R.string.error_fix_fields, Snackbar.LENGTH_SHORT).show();
            return;
        }

        vm.register(r);
    }

    /** Sets or clears the error on one field and remembers the first failing field. */
    private TextInputLayout check(TextInputLayout layout, @Nullable String error,
                                  @Nullable TextInputLayout firstError) {
        layout.setError(error);
        if (error != null && firstError == null) return layout;
        return firstError;
    }

    /** Y position of a view inside the ScrollView's content, for scrolling to it. */
    private int topInScroll(View view) {
        int top = 0;
        View v = view;
        while (v != null && v != binding.scroll) {
            top += v.getTop();
            v = (View) v.getParent();
        }
        return top;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (seedSnackbar != null) seedSnackbar.dismiss();
        binding = null;
    }
}
