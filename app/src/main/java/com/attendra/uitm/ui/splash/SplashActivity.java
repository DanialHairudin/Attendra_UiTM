package com.attendra.uitm.ui.splash;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import com.attendra.uitm.R;
import com.attendra.uitm.data.AuthRepository;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.ui.BaseActivity;
import com.attendra.uitm.ui.auth.AuthActivity;
import com.attendra.uitm.util.HomeRouter;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseUser;

/**
 * First screen (launcher).
 *
 * - Already signed in and verified → skip straight to the right home screen.
 * - Signed in but NOT verified (e.g. the app was closed on the Verify screen)
 *   → sign out, so only verified users are ever kept signed in.
 * - Otherwise show the logo with "Get started" (Log in) and "Create an account".
 */
public class SplashActivity extends BaseActivity {

    private final AuthRepository auth = new AuthRepository();
    private View btnGetStarted;
    private View btnCreateAccount;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        padForSystemBars(findViewById(R.id.splash_root));

        btnGetStarted = findViewById(R.id.btn_get_started);
        btnCreateAccount = findViewById(R.id.btn_create_account);
        btnGetStarted.setOnClickListener(v -> openAuth(AuthActivity.MODE_LOGIN));
        btnCreateAccount.setOnClickListener(v -> openAuth(AuthActivity.MODE_REGISTER));

        FirebaseUser user = auth.currentUser();
        if (user == null) return;
        if (!user.isEmailVerified()) {
            auth.signOut();
            return;
        }

        // Hide the buttons while the role loads, so they don't flash before routing.
        setButtonsVisible(false);
        auth.loadRole(user, new Callback<String>() {
            @Override
            public void onSuccess(String role) {
                if (isFinishing()) return; // user left the splash meanwhile
                HomeRouter.openHome(SplashActivity.this, role);
            }

            @Override
            public void onError(String message) {
                // Offline or no profile: stay here so the user can log in again.
                setButtonsVisible(true);
                Snackbar.make(findViewById(R.id.splash_root), message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void setButtonsVisible(boolean visible) {
        int v = visible ? View.VISIBLE : View.INVISIBLE;
        btnGetStarted.setVisibility(v);
        btnCreateAccount.setVisibility(v);
    }

    private void openAuth(String mode) {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.putExtra(AuthActivity.EXTRA_MODE, mode);
        startActivity(intent);
    }
}
