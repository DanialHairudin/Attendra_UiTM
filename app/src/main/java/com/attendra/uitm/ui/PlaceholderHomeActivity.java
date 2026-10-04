package com.attendra.uitm.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.attendra.uitm.R;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.UserRepository;
import com.attendra.uitm.databinding.ActivityHomePlaceholderBinding;
import com.attendra.uitm.util.HomeRouter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * C3 stand-in for the two home areas. It shows who is signed in and offers
 * Sign out, which is enough to test login and role routing. LecturerMainActivity
 * and StudentMainActivity extend it until their real screens are built.
 */
public abstract class PlaceholderHomeActivity extends BaseActivity {

    private ActivityHomePlaceholderBinding binding;

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

        new UserRepository().getName(user.getUid(), new Callback<String>() {
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
    }
}
