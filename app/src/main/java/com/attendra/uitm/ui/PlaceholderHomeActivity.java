package com.attendra.uitm.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.attendra.uitm.R;
import com.attendra.uitm.data.AuthRepository;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.UserRepository;
import com.attendra.uitm.databinding.ActivityHomePlaceholderBinding;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.util.HomeRouter;
import com.attendra.uitm.util.TestAccounts;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * C3 stand-in for the two home areas. It shows who is signed in and offers
 * Sign out, which is enough to test login and role routing. LecturerMainActivity
 * and StudentMainActivity extend it until their real screens are built.
 *
 * In DEBUG builds it also has the "Switch test account" row (CLAUDE.md 12.4).
 * That row moves to the Settings screen once Settings exists (C4).
 */
public abstract class PlaceholderHomeActivity extends BaseActivity {

    private ActivityHomePlaceholderBinding binding;

    /** Registration.ROLE_LECTURER or ROLE_STUDENT. */
    protected abstract String role();

    @StringRes
    protected abstract int roleLabel();

    @StringRes
    protected abstract int placeholderText();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            // Signed out somewhere else: go back to the start.
            HomeRouter.openSplash(this);
            return;
        }

        binding = ActivityHomePlaceholderBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        padForSystemBars(binding.homeRoot);

        binding.txtRole.setText(roleLabel());
        binding.txtEmail.setText(user.getEmail());
        binding.txtPlaceholder.setText(placeholderText());
        binding.txtGreeting.setText(R.string.home_greeting_plain);

        new UserRepository().getName(user.getUid(), role(), new Callback<String>() {
            @Override
            public void onSuccess(String name) {
                if (name != null && !isDestroyed()) {
                    binding.txtGreeting.setText(getString(R.string.home_greeting, name));
                }
            }

            @Override
            public void onError(String message) {
                // The greeting just stays without a name.
            }
        });

        binding.btnSignOut.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            HomeRouter.openSplash(this);
        });

        setUpSwitchTestAccount();
    }

    /**
     * DEBUG only: sign out, then sign in as the other test account
     * (lecturer ↔ student) and open that account's home screen.
     */
    private void setUpSwitchTestAccount() {
        String target = TestAccounts.otherRole(role());
        if (!TestAccounts.available(target)) return; // release build, or no test login set

        binding.rowSwitchTest.setVisibility(View.VISIBLE);
        binding.txtSwitchTestTarget.setText(Registration.ROLE_LECTURER.equals(target)
                ? R.string.tester_switch_to_lecturer : R.string.tester_switch_to_student);
        binding.rowSwitchTest.setOnClickListener(v -> {
            binding.rowSwitchTest.setEnabled(false);
            binding.btnSignOut.setEnabled(false);
            binding.txtSwitchTestTarget.setText(R.string.tester_switching);

            AuthRepository auth = new AuthRepository();
            auth.signOut();
            auth.login(TestAccounts.email(target), TestAccounts.password(target),
                    new Callback<String>() {
                        @Override
                        public void onSuccess(String result) {
                            if (AuthRepository.UNVERIFIED.equals(result)) {
                                // The test account is not marked verified in the database.
                                auth.signOut();
                                HomeRouter.openSplash(PlaceholderHomeActivity.this);
                            } else {
                                HomeRouter.openHome(PlaceholderHomeActivity.this, result);
                            }
                        }

                        @Override
                        public void onError(String message) {
                            // Already signed out, so the only safe place is the start.
                            Toast.makeText(PlaceholderHomeActivity.this, message,
                                    Toast.LENGTH_LONG).show();
                            HomeRouter.openSplash(PlaceholderHomeActivity.this);
                        }
                    });
        });
    }
}
