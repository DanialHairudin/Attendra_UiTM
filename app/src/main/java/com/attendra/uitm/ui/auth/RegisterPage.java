package com.attendra.uitm.ui.auth;

import static com.attendra.uitm.ui.auth.LoginPage.raw;
import static com.attendra.uitm.ui.auth.LoginPage.text;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.attendra.uitm.R;
import com.attendra.uitm.databinding.RegisterPageBinding;
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
import java.util.Locale;

/** Register screen. */
public class RegisterPage extends Fragment {

    private RegisterPageBinding binding;
    private RegisterViewModel vm;
    private Snackbar seedSnackbar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = RegisterPageBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(this).get(RegisterViewModel.class);

        binding.appbar.txtAppbarTitle.setText(R.string.register_title);
        binding.appbar.btnBack.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        requireActivity().getOnBackPressedDispatcher().onBackPressed();
                    }
                });

        binding.btnRoleStudent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setRole(Registration.ROLE_STUDENT);
            }
        });
        binding.btnRoleLecturer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setRole(Registration.ROLE_LECTURER);
            }
        });
        setRole(vm.role);

        // Long programme names wrap onto up to 3 lines instead of being cut off.
        binding.inputProgramme.setSingleLine(false);
        binding.inputProgramme.setMaxLines(3);

        binding.btnCreate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submit();
            }
        });

        watchViewModel();
        vm.loadSeedIfNeeded();
    }

    /** Updates the page when the sign-up state changes. */
    private void watchViewModel() {
        vm.getSubmitting().observe(getViewLifecycleOwner(), new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean loading) {
                binding.btnCreate.setEnabled(!loading);
                if (loading) {
                    binding.btnCreate.setText(R.string.creating_account);
                } else {
                    binding.btnCreate.setText(R.string.create_account_button);
                }
            }
        });
        vm.getRegisteredEmail().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(String email) {
                if (email != null) {
                    ((AuthPage) requireActivity()).showVerify(email, true);
                }
            }
        });
        vm.getRegisterError().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(String message) {
                if (message == null) return;
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
                vm.clearRegisterError(); // Show it once, not again after rotation
            }
        });

        vm.getSeedState().observe(getViewLifecycleOwner(), new Observer<RegisterViewModel.SeedState>() {
            @Override
            public void onChanged(RegisterViewModel.SeedState state) {
                onSeedState(state);
            }
        });
    }

    // ---------------------------------------------------------------- role toggle

    /** Shows the fields for one role and hides the other role's fields. */
    private void setRole(String role) {
        vm.role = role;
        boolean student = Registration.ROLE_STUDENT.equals(role);

        binding.btnRoleStudent.setChecked(student);
        binding.btnRoleLecturer.setChecked(!student);

        if (student) {
            binding.groupStudentId.setVisibility(View.VISIBLE);
            binding.groupAcademic.setVisibility(View.VISIBLE);
            binding.groupStaffId.setVisibility(View.GONE);
            binding.txtLabelClubs.setText(R.string.label_clubs_student);
        } else {
            binding.groupStudentId.setVisibility(View.GONE);
            binding.groupAcademic.setVisibility(View.GONE);
            binding.groupStaffId.setVisibility(View.VISIBLE);
            binding.txtLabelClubs.setText(R.string.label_clubs_lecturer);
        }

        // The UiTM email domain is required for both roles, so the label and example always show
        // the right format.
        if (student) {
            binding.layoutEmail.setPlaceholderText(getString(R.string.hint_email_student));
            binding.layoutEmail.setHint(R.string.label_email_student);
        } else {
            binding.layoutEmail.setPlaceholderText(getString(R.string.hint_email_staff));
            binding.layoutEmail.setHint(R.string.label_email_staff);
        }

        // Errors on fields that just got hidden would be confusing later.
        binding.layoutStudentId.setError(null);
        binding.layoutStaffId.setError(null);
        binding.layoutEmail.setError(null);
    }

    // ---------------------------------------------------------------- seed data

    /** Shows or hides things while programmes and clubs load. */
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
                bindTrack();
                bindClubChips();
                break;
            case ERROR:
                binding.txtClubsNote.setText(R.string.clubs_optional_note);
                seedSnackbar = Snackbar.make(binding.getRoot(),
                                getString(R.string.error_seed_load, vm.getSeedError()),
                                Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.retry, new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                vm.loadSeedIfNeeded();
                            }
                        });
                seedSnackbar.show();
                break;
        }
    }

    // In each dropdown below, "labels" holds the texts of the rows in the same order as the list
    // of items, so list.get(position) is the item that was tapped.

    /** Fills the Programme dropdown. */
    private void bindProgrammeDropdown() {
        List<Programme> programmes = new ArrayList<>(vm.programmes);
        List<String> labels = new ArrayList<>();
        for (Programme p : programmes) {
            labels.add(p.label());
        }
        binding.inputProgramme.setAdapter(new DropdownAdapter(requireContext(), labels));
        // Re-show the remembered choice (e.g. after rotation) in the same format.
        if (vm.programme != null) {
            binding.inputProgramme.setText(vm.programme.label(), false);
        }
        binding.inputProgramme.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                Programme chosen = programmes.get(position);
                binding.layoutProgramme.setError(null);
                if (vm.programme != null && vm.programme.code.equals(chosen.code)) return;
                // A new programme means the old part and group no longer apply.
                vm.programme = chosen;
                vm.part = 0;
                vm.groupId = null;
                vm.trackId = null; // Tracks belong to a programme
                binding.inputPart.setText("", false);
                binding.inputGroup.setText("", false);
                bindPartDropdown();
                bindGroupDropdown();
                bindTrack();
            }
        });
    }

    /** Fills the Part dropdown. */
    private void bindPartDropdown() {
        List<Integer> parts = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int i = 1; i <= vm.partCount(); i++) {
            parts.add(i);
            labels.add(getString(R.string.part_n, i));
        }
        binding.inputPart.setAdapter(new DropdownAdapter(requireContext(), labels));
        binding.inputPart.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                int chosen = parts.get(position);
                binding.layoutPart.setError(null);
                if (chosen == vm.part) return;
                // A new part means the old group no longer applies.
                vm.part = chosen;
                vm.groupId = null;
                binding.inputGroup.setText("", false);
                bindGroupDropdown();
                bindTrack();
            }
        });
        updateCascadeEnabled();
    }

    /** Fills the Group dropdown. */
    private void bindGroupDropdown() {
        List<Group> groups = vm.groupsForSelection();
        List<String> groupIds = new ArrayList<>();
        for (Group g : groups) {
            groupIds.add(g.id);
        }
        binding.inputGroup.setAdapter(new DropdownAdapter(requireContext(), groupIds));
        binding.inputGroup.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                vm.groupId = groupIds.get(position);
                binding.layoutGroup.setError(null);
                bindTrack();
            }
        });
        if (vm.programme != null && vm.part != 0 && groups.isEmpty()) {
            binding.layoutGroup.setError(getString(R.string.error_no_groups));
        }
        updateCascadeEnabled();
    }

    /**
     * Track dropdown, shown only when the chosen part has tracks. If the group already follows a
     * track (e.g. CDCS2306A is Big Data) it's filled in and locked; otherwise the student picks.
     */
    private void bindTrack() {
        boolean show = vm.needsTrack();
        if (show) {
            binding.groupTrack.setVisibility(View.VISIBLE);
        } else {
            binding.groupTrack.setVisibility(View.GONE);
        }
        if (!show) return;

        List<String> ids = vm.trackIds();
        List<String> labels = new ArrayList<>();
        for (String id : ids) {
            labels.add(vm.programme.trackLabel(id));
        }
        binding.inputTrack.setAdapter(new DropdownAdapter(requireContext(), labels));
        String locked = vm.groupTrack();
        if (locked != null) {
            binding.inputTrack.setText(vm.programme.trackLabel(locked), false);
            binding.layoutTrack.setEnabled(false);
            binding.layoutTrack.setError(null);
            binding.txtTrackNote.setText(R.string.track_note_group);
            return;
        }
        if (vm.trackId != null) {
            binding.inputTrack.setText(vm.programme.trackLabel(vm.trackId), false);
        } else {
            binding.inputTrack.setText("", false);
        }
        binding.layoutTrack.setEnabled(true);
        binding.txtTrackNote.setText(R.string.track_note_pick);
        binding.inputTrack.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                vm.trackId = ids.get(position);
                binding.layoutTrack.setError(null);
            }
        });
    }

    /** Part unlocks after a programme is chosen; Group unlocks after a part is chosen. */
    private void updateCascadeEnabled() {
        binding.layoutPart.setEnabled(vm.programme != null);
        binding.layoutGroup.setEnabled(vm.programme != null && vm.part != 0);
    }

    /** One pill per club. */
    private void bindClubChips() {
        binding.chipsClubs.removeAllViews();
        for (Club club : vm.clubs) {
            Chip chip = new Chip(requireContext()); // Picks up Widget.Attendra.Pill from the theme
            chip.setText(club.shortName);
            chip.setContentDescription(club.name);
            chip.setCheckable(true);
            chip.setChecked(vm.selectedClubs.contains(club.id));
            // The ViewModel remembers the choices, so the chip doesn't need to save its own state.
            chip.setSaveEnabled(false);
            chip.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton button, boolean checked) {
                    if (checked) {
                        vm.selectedClubs.add(club.id);
                    } else {
                        vm.selectedClubs.remove(club.id);
                    }
                }
            });
            binding.chipsClubs.addView(chip);
        }
    }

    // ---------------------------------------------------------------- submit

    /** Create account: reads the fields, checks them, then registers. */
    private void submit() {
        // SECTION 1: read the fields
        Registration r = new Registration();
        r.role = vm.role;
        r.name = text(binding.inputName.getText()).replaceAll("\\s+", " ");
        r.email = text(binding.inputEmail.getText()).toLowerCase(Locale.ROOT);
        String phoneInput = text(binding.inputPhone.getText());
        r.phone = "+60" + Validators.phoneDigits(phoneInput);
        r.password = raw(binding.inputPassword.getText());
        String confirm = raw(binding.inputConfirm.getText());
        r.clubIds.addAll(vm.selectedClubs);

        // SECTION 2: check them. Each check puts its message under the field (or clears it when OK).
        TextInputLayout firstError = null;
        firstError = check(binding.layoutName, Validators.name(r.name), firstError);
        if (r.isStudent()) {
            r.studentId = text(binding.inputStudentId.getText());
            firstError = check(binding.layoutStudentId, Validators.studentId(r.studentId), firstError);
        } else {
            r.staffId = text(binding.inputStaffId.getText()).toUpperCase(Locale.ROOT);
            firstError = check(binding.layoutStaffId, Validators.staffId(r.staffId), firstError);
        }
        firstError = check(binding.layoutEmail, Validators.registerEmail(r.email, r.role), firstError);
        firstError = check(binding.layoutPhone, Validators.phone(phoneInput), firstError);
        if (r.isStudent()) {
            firstError = checkAcademic(r, firstError);
        }
        firstError = check(binding.layoutPassword, Validators.password(r.password), firstError);
        firstError = check(binding.layoutConfirm, Validators.confirmPassword(r.password, confirm),
                firstError);

        // SECTION 3: stop at the first error, or create the account
        if (firstError != null) {
            scrollToError(firstError);
            return;
        }
        vm.register(r);
    }

    /** Students only: checks programme, part, group and track, and copies them into r. */
    private TextInputLayout checkAcademic(Registration r, @Nullable TextInputLayout firstError) {
        if (vm.programme == null) {
            firstError = check(binding.layoutProgramme,
                    getString(R.string.error_choose_programme), firstError);
        } else {
            firstError = check(binding.layoutProgramme, null, firstError);
        }
        if (vm.part == 0) {
            firstError = check(binding.layoutPart, getString(R.string.error_choose_part),
                    firstError);
        } else {
            firstError = check(binding.layoutPart, null, firstError);
        }
        if (vm.groupId == null) {
            firstError = check(binding.layoutGroup, getString(R.string.error_choose_group),
                    firstError);
        } else {
            firstError = check(binding.layoutGroup, null, firstError);
        }
        // Only asked when the part has tracks and the group doesn't already set one.
        boolean pickTrack = vm.needsTrack() && vm.groupId != null && vm.groupTrack() == null;
        if (vm.needsTrack()) {
            if (pickTrack && vm.trackId == null) {
                firstError = check(binding.layoutTrack, getString(R.string.error_choose_track),
                        firstError);
            } else {
                firstError = check(binding.layoutTrack, null, firstError);
            }
        }
        if (vm.programme != null) {
            r.programme = vm.programme.code;
            r.part = vm.part;
            r.groupId = vm.groupId;
            if (pickTrack) {
                r.track = vm.trackId;
            } else {
                r.track = null;
            }
        }
        return firstError;
    }

    /** Scrolls to the first field with an error. */
    private void scrollToError(TextInputLayout target) {
        target.requestFocus();
        binding.scroll.post(new Runnable() {
            @Override
            public void run() {
                if (binding != null) {
                    binding.scroll.smoothScrollTo(0, Math.max(0, topInScroll(target) - 120));
                }
            }
        });
        Snackbar.make(binding.getRoot(), R.string.error_fix_fields, Snackbar.LENGTH_SHORT).show();
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
        if (seedSnackbar != null) {
            seedSnackbar.dismiss();
        }
        binding = null;
    }
}
