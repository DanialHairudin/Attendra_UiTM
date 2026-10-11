package com.attendra.uitm.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.attendra.uitm.R;
import com.attendra.uitm.data.AuthRepository;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.Subscriptions;
import com.attendra.uitm.data.UserRepository;
import com.attendra.uitm.databinding.FragmentSettingsBinding;
import com.attendra.uitm.databinding.ItemSettingRowBinding;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.ui.common.Ui;
import com.attendra.uitm.util.HomeRouter;
import com.attendra.uitm.util.Names;
import com.attendra.uitm.util.TestAccounts;
import com.attendra.uitm.util.ThemePrefs;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;

/**
 * Settings Page for both roles: profile button, theme (Light / Dark / System),
 * My clubs, the DEBUG-only "Switch test account" row (CLAUDE.md 12.4) and Log out.
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

        clubsRow = addRow(binding.listSettings, Registration.ROLE_LECTURER.equals(role)
                ? R.string.label_clubs_lecturer : R.string.label_clubs_student);
        clubsRow.getRoot().setOnClickListener(v -> MyClubsActivity.open(requireContext(), role));

        setUpSwitchTestAccount();

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

    // ------------------------------------------------------ switch test account

    /**
     * DEBUG only: sign out, then sign in as the other test account
     * (lecturer ↔ student) and open that account's dashboard.
     */
    private void setUpSwitchTestAccount() {
        String target = TestAccounts.otherRole(role);
        if (!TestAccounts.available(target)) return; // release build, or no test login set

        binding.cardTester.setVisibility(View.VISIBLE);
        ItemSettingRowBinding row = addRow(binding.listTester, R.string.tester_switch);
        row.settingSubtitle.setText(Registration.ROLE_LECTURER.equals(target)
                ? R.string.tester_switch_to_lecturer : R.string.tester_switch_to_student);
        row.getRoot().setOnClickListener(v -> {
            row.getRoot().setEnabled(false);
            binding.btnLogOut.setEnabled(false);
            row.settingSubtitle.setText(R.string.tester_switching);

            AuthRepository auth = new AuthRepository();
            auth.signOut();
            auth.login(TestAccounts.email(target), TestAccounts.password(target),
                    new Callback<AuthRepository.Route>() {
                        @Override
                        public void onSuccess(AuthRepository.Route route) {
                            if (getActivity() == null) return;
                            if (route.unverified) {
                                // The test account is not marked verified in the database.
                                auth.signOut();
                                HomeRouter.openSplash(getActivity());
                            } else {
                                HomeRouter.open(getActivity(), route);
                            }
                        }

                        @Override
                        public void onError(String message) {
                            if (getActivity() == null) return;
                            // Already signed out, so the only safe place is the start.
                            Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
                            HomeRouter.openSplash(getActivity());
                        }
                    });
        });
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
