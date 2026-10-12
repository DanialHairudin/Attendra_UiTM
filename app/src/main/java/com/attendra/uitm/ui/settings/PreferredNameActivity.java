package com.attendra.uitm.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.annotation.Nullable;

import com.attendra.uitm.R;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.Db;
import com.attendra.uitm.data.TeachingRepository;
import com.attendra.uitm.data.UserRepository;
import com.attendra.uitm.databinding.ActivityPreferredNameBinding;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.ui.BaseActivity;
import com.attendra.uitm.ui.common.Ui;
import com.attendra.uitm.ui.lecturer.AddTeachingClassActivity;
import com.attendra.uitm.util.DropdownAdapter;
import com.attendra.uitm.util.HomeRouter;
import com.attendra.uitm.util.NameSuggestions;
import com.attendra.uitm.util.Names;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Preferred Name Page, "What should we call you?" (CLAUDE.md 12.6).
 *
 * Two ways in:
 * - first time: after the email code is verified, or at login when
 *   preferredName is missing. Saving opens the dashboard.
 * - edit: from the Profile Page. Saving goes back to the profile.
 *
 * The chips are suggestions from NameSuggestions plus "Other", which shows a
 * text field (2–20 letters and spaces). Lecturers also pick a title. The card
 * at the bottom previews the dashboard greeting as the choice changes.
 */
public class PreferredNameActivity extends BaseActivity {

    private static final String EXTRA_ROLE = "role";
    private static final String EXTRA_FIRST_TIME = "first_time";
    private static final String STATE_CHOICE = "choice";
    private static final String STATE_OTHER = "other";
    private static final String STATE_TITLE = "title";

    /** Intent for the first-time page (no back button; Save opens the dashboard). */
    public static Intent firstTimeIntent(Context from, String role) {
        return new Intent(from, PreferredNameActivity.class)
                .putExtra(EXTRA_ROLE, role)
                .putExtra(EXTRA_FIRST_TIME, true);
    }

    /** Opens the page from the Profile Page to change the name or title. */
    public static void openToEdit(Context from, String role) {
        from.startActivity(new Intent(from, PreferredNameActivity.class)
                .putExtra(EXTRA_ROLE, role)
                .putExtra(EXTRA_FIRST_TIME, false));
    }

    private ActivityPreferredNameBinding binding;
    private String role;
    private boolean firstTime;
    private boolean lecturer;
    private String uid;

    /** The chosen suggestion, or null when "Other" is selected. */
    private String choice;
    private boolean otherSelected;
    /** "" means None. */
    private String title = "";
    private boolean loaded;
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
        role = getIntent().getStringExtra(EXTRA_ROLE);
        firstTime = getIntent().getBooleanExtra(EXTRA_FIRST_TIME, true);
        lecturer = Registration.ROLE_LECTURER.equals(role);

        binding = ActivityPreferredNameBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        padForSystemBars(binding.nameRoot);

        // First time: no app bar (there is nowhere to go back to). Edit: back arrow.
        binding.appbar.getRoot().setVisibility(firstTime ? View.GONE : View.VISIBLE);
        binding.appbar.txtAppbarTitle.setText(R.string.profile_preferred_name);
        binding.appbar.btnBack.setOnClickListener(v -> finish());
        binding.txtEyebrow.setVisibility(firstTime ? View.VISIBLE : View.GONE);
        binding.btnSave.setText(firstTime ? R.string.name_continue : R.string.name_save);
        binding.btnSave.setEnabled(false);
        binding.btnSave.setOnClickListener(v -> save());
        binding.groupTitle.setVisibility(lecturer ? View.VISIBLE : View.GONE);

        binding.inputOther.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                binding.layoutOther.setError(null);
                updatePreview();
            }
        });

        // After a rotation, keep what the user had picked instead of the saved values.
        loadProfile(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (!loaded) return;
        outState.putString(STATE_CHOICE, otherSelected ? null : choice);
        outState.putBoolean(STATE_OTHER, otherSelected);
        outState.putString(STATE_TITLE, title);
    }

    /** Reads the full name and any saved preferred name / title once. */
    private void loadProfile(@Nullable Bundle restored) {
        Db.readOnce(UserRepository.profileRef(uid, role), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot profile) {
                String fullName = profile.child("name").getValue(String.class);
                String saved = profile.child("preferredName").getValue(String.class);
                String savedTitle = profile.child("title").getValue(String.class);
                bind(fullName, saved, savedTitle, restored);
            }

            @Override
            public void onError(String message) {
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void bind(String fullName, @Nullable String saved, @Nullable String savedTitle,
                      @Nullable Bundle restored) {
        List<String> suggestions = NameSuggestions.suggest(fullName);

        // What to select: the restored choice, else the saved name, else the first suggestion.
        String select;
        boolean other;
        if (restored != null) {
            select = restored.getString(STATE_CHOICE);
            other = restored.getBoolean(STATE_OTHER);
            title = restored.getString(STATE_TITLE, "");
        } else {
            boolean hasSaved = saved != null && !saved.trim().isEmpty();
            other = hasSaved && !suggestions.contains(saved);
            select = hasSaved ? saved : (suggestions.isEmpty() ? null : suggestions.get(0));
            if (other) binding.inputOther.setText(saved);
            title = savedTitle == null ? "" : savedTitle;
        }
        if (suggestions.isEmpty()) other = true;

        binding.chipsNames.removeAllViews();
        for (String s : suggestions) {
            Chip chip = addChip(s);
            chip.setOnCheckedChangeListener((b, checked) -> {
                if (checked) selectSuggestion(s);
            });
            if (!other && s.equals(select)) chip.setChecked(true);
        }
        Chip otherChip = addChip(getString(R.string.name_other));
        otherChip.setOnCheckedChangeListener((b, checked) -> {
            if (checked) selectOther();
        });
        if (other) otherChip.setChecked(true);
        if (other) selectOther(); else selectSuggestion(select);

        if (lecturer) bindTitles();
        loaded = true;
        binding.btnSave.setEnabled(true);
        updatePreview();
    }

    private Chip addChip(String text) {
        Chip chip = new Chip(this); // picks up Widget.Attendra.Pill from the theme
        chip.setText(text);
        chip.setCheckable(true);
        chip.setSaveEnabled(false); // this screen restores the choice itself
        binding.chipsNames.addView(chip);
        return chip;
    }

    private void selectSuggestion(String name) {
        choice = name;
        otherSelected = false;
        binding.layoutOther.setVisibility(View.GONE);
        updatePreview();
    }

    private void selectOther() {
        choice = null;
        otherSelected = true;
        binding.layoutOther.setVisibility(View.VISIBLE);
        binding.inputOther.requestFocus();
        updatePreview();
    }

    /** Title dropdown: Dr., Prof. Madya, Prof., Encik, Puan, Cik, None. */
    private void bindTitles() {
        DropdownAdapter<String> adapter = new DropdownAdapter<>(this, Names.LECTURER_TITLES,
                t -> t.isEmpty() ? getString(R.string.name_title_none) : t);
        binding.inputTitle.setAdapter(adapter);
        binding.inputTitle.setText(adapter.labelOf(title), false);
        binding.inputTitle.setOnItemClickListener((parent, v, position, id) -> {
            title = (String) parent.getItemAtPosition(position);
            updatePreview();
        });
    }

    /** The name that would be saved right now, or null if "Other" is not valid yet. */
    @Nullable
    private String currentName() {
        if (!otherSelected) return choice;
        CharSequence typed = binding.inputOther.getText();
        return NameSuggestions.validOther(typed == null ? "" : typed.toString());
    }

    /** "Good morning, Dr. Aina" for lecturers, "Hi, Danial" for students. */
    private void updatePreview() {
        String name = currentName();
        if (name == null) {
            binding.txtPreview.setText(R.string.dash);
            return;
        }
        String shown = Names.greetingName(null, name, lecturer ? title : null);
        binding.txtPreview.setText(lecturer
                ? Ui.greeting(this, shown, System.currentTimeMillis())
                : getString(R.string.greeting_student, shown));
    }

    /** Saves preferredName (and title for lecturers) in one updateChildren. */
    private void save() {
        if (saving || !loaded) return;
        String name = currentName();
        if (name == null) {
            binding.layoutOther.setError(getString(R.string.name_other_error));
            return;
        }
        saving = true;
        binding.btnSave.setEnabled(false);

        String base = UserRepository.profileNode(role) + "/" + uid + "/";
        Map<String, Object> updates = new HashMap<>();
        updates.put(base + "preferredName", name);
        if (lecturer) updates.put(base + "title", title.isEmpty() ? null : title); // null = None
        Db.write(updates, new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                if (!firstTime) {
                    finish();
                } else if (lecturer) {
                    openLecturerNext();
                } else {
                    HomeRouter.openHome(PreferredNameActivity.this, role);
                }
            }

            @Override
            public void onError(String message) {
                saving = false;
                binding.btnSave.setEnabled(true);
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    /**
     * C5b: a new lecturer with no class this semester sets up their teaching
     * classes next (with "Skip for now"); otherwise the dashboard opens.
     * If the check fails (e.g. offline), go to the dashboard anyway.
     */
    private void openLecturerNext() {
        TeachingRepository.hasCurrent(uid, new Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean hasClasses) {
                if (hasClasses) {
                    HomeRouter.openHome(PreferredNameActivity.this, role);
                } else {
                    startActivity(AddTeachingClassActivity.setupIntent(PreferredNameActivity.this));
                    finish();
                }
            }

            @Override
            public void onError(String message) {
                HomeRouter.openHome(PreferredNameActivity.this, role);
            }
        });
    }
}
