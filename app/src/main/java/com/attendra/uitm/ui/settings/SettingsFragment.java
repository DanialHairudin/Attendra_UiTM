package com.attendra.uitm.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.util.TypedValue;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;

import com.attendra.uitm.R;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.Subscriptions;
import com.attendra.uitm.data.UserRepository;
import com.attendra.uitm.databinding.FragmentSettingsBinding;
import com.attendra.uitm.databinding.ItemSettingRowBinding;
import com.attendra.uitm.databinding.ItemSwitchRowBinding;
import com.attendra.uitm.databinding.SheetTextSizeBinding;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.ui.common.Ui;
import com.attendra.uitm.ui.student.StudentMainActivity;
import com.attendra.uitm.util.HomeRouter;
import com.attendra.uitm.util.NotificationPrefs;
import com.attendra.uitm.util.Names;
import com.attendra.uitm.util.TextSizePrefs;
import com.attendra.uitm.util.ThemePrefs;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;

/**
 * Settings Page for both roles: profile button, theme (Light / Dark / System),
 * text size, My clubs, session notifications (students) and Log out.
 */
public class SettingsFragment extends Fragment {

    private static final String ARG_ROLE = "role";

    private static final String[] THEME_MODES =
            {ThemePrefs.MODE_LIGHT, ThemePrefs.MODE_DARK, ThemePrefs.MODE_SYSTEM};
    private static final int[] THEME_LABELS =
            {R.string.theme_light, R.string.theme_dark, R.string.theme_system};

    public static SettingsFragment newInstance(String role) {
        Bundle args = new Bundle();
        args.putString(ARG_ROLE, role);
        SettingsFragment f = new SettingsFragment();
        f.setArguments(args);
        return f;
    }

    private FragmentSettingsBinding binding;
    private final Subscriptions subs = new Subscriptions();
    private String role;
    private ItemSettingRowBinding themeRow;
    private ItemSettingRowBinding textSizeRow;
    private ItemSettingRowBinding clubsRow;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        role = requireArguments().getString(ARG_ROLE);

        binding.cardProfile.setOnClickListener(v -> ProfileActivity.open(requireContext(), role));

        themeRow = addRow(binding.listSettings, R.string.theme);
        themeRow.settingSubtitle.setText(THEME_LABELS[themeIndex()]);
        themeRow.getRoot().setOnClickListener(v -> chooseTheme());

        textSizeRow = addRow(binding.listSettings, R.string.text_size);
        textSizeRow.settingSubtitle.setText(
                TextSizePrefs.label(TextSizePrefs.get(requireContext())));
        textSizeRow.getRoot().setOnClickListener(v -> chooseTextSize());

        clubsRow = addRow(binding.listSettings, Registration.ROLE_LECTURER.equals(role)
                ? R.string.label_clubs_lecturer : R.string.label_clubs_student);
        clubsRow.getRoot().setOnClickListener(v -> MyClubsActivity.open(requireContext(), role));

        // Only students receive notifications.
        if (Registration.ROLE_STUDENT.equals(role)) addNotificationsRow();

        binding.btnLogOut.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            HomeRouter.openSplash(requireActivity());
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String uid = user.getUid();

        subs.listen(UserRepository.profileRef(uid, role), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot profile) {
                String name = profile.child("name").getValue(String.class);
                if (binding == null || name == null) return;
                binding.txtName.setText(name);
                binding.txtAvatar.setText(Names.initials(name));
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });

        subs.listen(UserRepository.clubsQuery(uid, role), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                if (binding == null) return;
                int n = UserRepository.clubIds(snapshot, uid, role).size();
                clubsRow.settingSubtitle.setText(
                        getResources().getQuantityString(R.plurals.clubs_count, n, n));
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        subs.clear();
    }

    // ------------------------------------------------------------------- theme

    private int themeIndex() {
        String mode = ThemePrefs.get(requireContext());
        for (int i = 0; i < THEME_MODES.length; i++) {
            if (THEME_MODES[i].equals(mode)) return i;
        }
        return 2; // system
    }

    /**
     * Saves the choice in SharedPreferences ("theme_mode") and applies it.
     * AppCompat then recreates the open screens in the new colours.
     */
    private void chooseTheme() {
        String[] labels = new String[THEME_LABELS.length];
        for (int i = 0; i < labels.length; i++) labels[i] = getString(THEME_LABELS[i]);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.theme)
                .setSingleChoiceItems(labels, themeIndex(), (dialog, which) -> {
                    dialog.dismiss();
                    ThemePrefs.save(requireContext(), THEME_MODES[which]);
                    ThemePrefs.apply(THEME_MODES[which]);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // --------------------------------------------------------------- text size

    /**
     * Bottom sheet with a live preview and a 4-step slider. Apply saves the
     * level (TextSizePrefs) and recreates the screen, so BaseActivity applies
     * the new font scale. Screens opened later pick it up when they start.
     */
    private void chooseTextSize() {
        int current = TextSizePrefs.get(requireContext());
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        SheetTextSizeBinding s = SheetTextSizeBinding.inflate(getLayoutInflater());
        sheet.setContentView(s.getRoot());

        // The sheet is already drawn at the current scale, so the preview is
        // resized relative to it: size × (new scale / current scale).
        float titlePx = s.previewTitle.getTextSize();
        float bodyPx = s.previewBody.getTextSize();
        Slider.OnChangeListener preview = (slider, value, fromUser) -> {
            int level = (int) value;
            float ratio = TextSizePrefs.scale(level) / TextSizePrefs.scale(current);
            s.previewTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, titlePx * ratio);
            s.previewBody.setTextSize(TypedValue.COMPLEX_UNIT_PX, bodyPx * ratio);
            s.txtLevel.setText(TextSizePrefs.label(level));
            ViewCompat.setStateDescription(s.sliderSize, getString(TextSizePrefs.label(level)));
        };
        s.sliderSize.setLabelFormatter(value -> getString(TextSizePrefs.label((int) value)));
        s.sliderSize.addOnChangeListener(preview);
        s.sliderSize.setValue(current);
        preview.onValueChange(s.sliderSize, current, false);

        s.btnApply.setOnClickListener(v -> {
            int level = (int) s.sliderSize.getValue();
            sheet.dismiss();
            if (level == current) return;
            TextSizePrefs.save(requireContext(), level);
            requireActivity().recreate();
        });
        sheet.show();
    }

    /** "Session notifications" on/off (NotificationPrefs). Off hides the unread badge. */
    private void addNotificationsRow() {
        ItemSwitchRowBinding row = ItemSwitchRowBinding.inflate(getLayoutInflater(),
                binding.listSettings, false);
        row.switchTitle.setText(R.string.settings_notifications);
        row.switchSubtitle.setText(R.string.settings_notifications_note);
        row.switchToggle.setContentDescription(getString(R.string.settings_notifications));
        row.switchToggle.setChecked(NotificationPrefs.isOn(requireContext()));
        row.switchToggle.setOnCheckedChangeListener((b, on) -> {
            NotificationPrefs.save(requireContext(), on);
            if (getActivity() instanceof StudentMainActivity) {
                ((StudentMainActivity) getActivity()).refreshBadge();
            }
        });
        row.getRoot().setOnClickListener(v -> row.switchToggle.toggle());
        Ui.addRow(binding.listSettings, row.getRoot());
    }

    // ----------------------------------------------------------------- helpers

    private ItemSettingRowBinding addRow(android.widget.LinearLayout list, @StringRes int title) {
        ItemSettingRowBinding row = ItemSettingRowBinding.inflate(getLayoutInflater(), list, false);
        row.settingTitle.setText(title);
        Ui.addRow(list, row.getRoot());
        return row;
    }

    private void showError(String message) {
        if (binding != null) Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
