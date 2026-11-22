package com.attendra.uitm.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CompoundButton;

import androidx.annotation.Nullable;

import com.attendra.uitm.R;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.Db;
import com.attendra.uitm.data.TeachingJava;
import com.attendra.uitm.data.UserJava;
import com.attendra.uitm.databinding.PreferredNamePageBinding;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.ui.BasePage;
import com.attendra.uitm.ui.common.Ui;
import com.attendra.uitm.ui.lecturer.AddTeachingClassPage;
import com.attendra.uitm.util.DropdownAdapter;
import com.attendra.uitm.util.HomeRouter;
import com.attendra.uitm.util.NameSuggestions;
import com.attendra.uitm.util.Names;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Preferred Name Page, "What should we call you?". */
public class PreferredNamePage extends BasePage {

    private static final String EXTRA_ROLE = "role";
    private static final String EXTRA_FIRST_TIME = "first_time";
    private static final String STATE_CHOICE = "choice";
    private static final String STATE_OTHER = "other";
    private static final String STATE_TITLE = "title";

    /** Intent for the first-time page (no back button; Save opens the dashboard). */
    public static Intent firstTimeIntent(Context from, String role) {
        return new Intent(from, PreferredNamePage.class)
                .putExtra(EXTRA_ROLE, role)
                .putExtra(EXTRA_FIRST_TIME, true);
    }

    /** Opens the page from the Profile Page to change the name or title. */
    public static void openToEdit(Context from, String role) {
        from.startActivity(new Intent(from, PreferredNamePage.class)
                .putExtra(EXTRA_ROLE, role)
                .putExtra(EXTRA_FIRST_TIME, false));
    }

    private PreferredNamePageBinding binding;
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

        binding = PreferredNamePageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        padForSystemBars(binding.nameRoot);

        showTitle();
        binding.btnSave.setEnabled(false);
        setUpInputs();

        // After a rotation, keep what the user had picked instead of the saved values.
        loadProfile(savedInstanceState);
    }

    /** Title bar and button text for first time / edit. */
    private void showTitle() {
        // First time: no app bar (there is nowhere to go back to).
        if (firstTime) {
            binding.appbar.getRoot().setVisibility(View.GONE);
        } else {
            binding.appbar.getRoot().setVisibility(View.VISIBLE);
        }
        binding.appbar.txtAppbarTitle.setText(R.string.profile_preferred_name);
        binding.appbar.btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        if (firstTime) {
            binding.txtEyebrow.setVisibility(View.VISIBLE);
        } else {
            binding.txtEyebrow.setVisibility(View.GONE);
        }
        if (firstTime) {
            binding.btnSave.setText(R.string.name_continue);
        } else {
            binding.btnSave.setText(R.string.name_save);
        }
    }

    /** Save button, title dropdown and the "Other" field. */
    private void setUpInputs() {
        binding.btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        if (lecturer) {
            binding.groupTitle.setVisibility(View.VISIBLE);
        } else {
            binding.groupTitle.setVisibility(View.GONE);
        }

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
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (!loaded) return;
        if (otherSelected) {
            outState.putString(STATE_CHOICE, null);
        } else {
            outState.putString(STATE_CHOICE, choice);
        }
        outState.putBoolean(STATE_OTHER, otherSelected);
        outState.putString(STATE_TITLE, title);
    }

    /** Reads the full name and any saved preferred name / title once. */
    private void loadProfile(@Nullable Bundle restored) {
        Db.readOnce(UserJava.profileRef(uid, role), new Callback<DataSnapshot>() {
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

    /** Fills the page once the profile is loaded. */
    private void bind(String fullName, @Nullable String saved, @Nullable String savedTitle,
                      @Nullable Bundle restored) {
        List<String> suggestions = NameSuggestions.suggest(fullName);

        // SECTION 1: what to select: the restored choice, else the saved name, else the first
        // suggestion
        String select;
        boolean other;
        if (restored != null) {
            select = restored.getString(STATE_CHOICE);
            other = restored.getBoolean(STATE_OTHER);
            title = restored.getString(STATE_TITLE, "");
        } else {
            boolean hasSaved = saved != null && !saved.trim().isEmpty();
            other = hasSaved && !suggestions.contains(saved);
            if (hasSaved) {
                select = saved;
            } else if (suggestions.isEmpty()) {
                select = null;
            } else {
                select = suggestions.get(0);
            }
            if (other) {
                binding.inputOther.setText(saved);
            }
            if (savedTitle == null) {
                title = "";
            } else {
                title = savedTitle;
            }
        }
        if (suggestions.isEmpty()) {
            other = true;
        }

        // SECTION 2: show the chips
        showNameChips(suggestions, select, other);

        // SECTION 3: the rest of the page
        if (lecturer) {
            bindTitles();
        }
        loaded = true;
        binding.btnSave.setEnabled(true);
        updatePreview();
    }

    /** One chip per suggested name, plus "Other". */
    private void showNameChips(List<String> suggestions, @Nullable String select, boolean other) {
        binding.chipsNames.removeAllViews();
        for (String s : suggestions) {
            Chip chip = addChip(s);
            chip.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton b, boolean checked) {
                    if (checked) {
                        selectSuggestion(s);
                    }
                }
            });
            if (!other && s.equals(select)) {
                chip.setChecked(true);
            }
        }
        Chip otherChip = addChip(getString(R.string.name_other));
        otherChip.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                if (checked) {
                    selectOther();
                }
            }
        });
        if (other) {
            otherChip.setChecked(true);
            selectOther();
        } else {
            selectSuggestion(select);
        }
    }

    /** Adds one name chip. */
    private Chip addChip(String text) {
        Chip chip = new Chip(this); // Picks up Widget.Attendra.Pill from the theme
        chip.setText(text);
        chip.setCheckable(true);
        chip.setSaveEnabled(false); // This screen restores the choice itself
        binding.chipsNames.addView(chip);
        return chip;
    }

    /** A suggested name was chosen. */
    private void selectSuggestion(String name) {
        choice = name;
        otherSelected = false;
        binding.layoutOther.setVisibility(View.GONE);
        updatePreview();
    }

    /** "Other" was chosen: shows the text field. */
    private void selectOther() {
        choice = null;
        otherSelected = true;
        binding.layoutOther.setVisibility(View.VISIBLE);
        binding.inputOther.requestFocus();
        updatePreview();
    }

    /** Title dropdown: Dr., Prof. Madya, Prof., Encik, Puan, Cik, None. */
    private void bindTitles() {
        List<String> labels = new ArrayList<>();
        for (String t : Names.LECTURER_TITLES) {
            labels.add(titleLabel(t));
        }
        binding.inputTitle.setAdapter(new DropdownAdapter(this, labels));
        binding.inputTitle.setText(titleLabel(title), false);
        binding.inputTitle.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                title = Names.LECTURER_TITLES.get(position);
                updatePreview();
            }
        });
    }

    /** The title as shown in the dropdown ("" is shown as "None"). */
    private String titleLabel(String t) {
        if (t.isEmpty()) {
            return getString(R.string.name_title_none);
        }
        return t;
    }

    /** The name that would be saved right now, or null if "Other" is not valid yet. */
    @Nullable
    private String currentName() {
        if (!otherSelected) return choice;
        CharSequence typed = binding.inputOther.getText();
        if (typed == null) {
            return NameSuggestions.validOther("");
        }
        return NameSuggestions.validOther(typed.toString());
    }

    /** "Good morning, Dr. Aina" for lecturers, "Hi, Danial" for students. */
    private void updatePreview() {
        String name = currentName();
        if (name == null) {
            binding.txtPreview.setText(R.string.dash);
            return;
        }
        String shown;
        if (lecturer) {
            shown = Names.greetingName(null, name, title);
        } else {
            shown = Names.greetingName(null, name, null);
        }
        if (lecturer) {
            binding.txtPreview.setText(Ui.greeting(this, shown, System.currentTimeMillis()));
        } else {
            binding.txtPreview.setText(getString(R.string.greeting_student, shown));
        }
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

        String base = UserJava.profileNode(role) + "/" + uid + "/";
        Map<String, Object> updates = new HashMap<>();
        updates.put(base + "preferredName", name);
        if (lecturer) {
            if (title.isEmpty()) {
                updates.put(base + "title", null); // Null = None
            } else {
                updates.put(base + "title", title);
            }
        }
        Db.write(updates, new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                if (!firstTime) {
                    finish();
                } else if (lecturer) {
                    openLecturerNext();
                } else {
                    HomeRouter.openHome(PreferredNamePage.this, role);
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
     * A new lecturer with no class this semester sets up their teaching classes next (with "Skip
     * for now"); otherwise the dashboard opens.
     */
    private void openLecturerNext() {
        TeachingJava.hasCurrent(uid, new Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean hasClasses) {
                if (hasClasses) {
                    HomeRouter.openHome(PreferredNamePage.this, role);
                } else {
                    startActivity(AddTeachingClassPage.setupIntent(PreferredNamePage.this));
                    finish();
                }
            }

            @Override
            public void onError(String message) {
                HomeRouter.openHome(PreferredNamePage.this, role);
            }
        });
    }
}
