package com.attendra.uitm.ui.splash;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import com.attendra.uitm.R;
import com.attendra.uitm.data.AuthJava;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.ui.BasePage;
import com.attendra.uitm.ui.auth.AuthPage;
import com.attendra.uitm.util.HomeRouter;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseUser;

/** First screen (launcher). */
public class SplashPage extends BasePage {

    private final AuthJava auth = new AuthJava();
    private View btnGetStarted;
    private View btnCreateAccount;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.splash_page);
        padForSystemBars(findViewById(R.id.splash_root));

        btnGetStarted = findViewById(R.id.btn_get_started);
        btnCreateAccount = findViewById(R.id.btn_create_account);
        btnGetStarted.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAuth(AuthPage.MODE_LOGIN);
            }
        });
        btnCreateAccount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAuth(AuthPage.MODE_REGISTER);
            }
        });

        FirebaseUser user = auth.currentUser();
        if (user == null) return;

        // Hide the buttons while the role loads, so they don't flash before routing.
        setButtonsVisible(false);
        auth.resolveHome(user, new Callback<AuthJava.Route>() {
            @Override
            public void onSuccess(AuthJava.Route route) {
                if (isFinishing()) return; // User left the splash meanwhile
                if (route.unverified) {
                    auth.signOut();
                    setButtonsVisible(true);
                    return;
                }
                HomeRouter.open(SplashPage.this, route);
            }

            @Override
            public void onError(String message) {
                // Offline or no profile: stay here so the user can log in again.
                setButtonsVisible(true);
                Snackbar.make(findViewById(R.id.splash_root), message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    /** Shows or hides the two buttons. */
    private void setButtonsVisible(boolean visible) {
        int v;
        if (visible) {
            v = View.VISIBLE;
        } else {
            v = View.INVISIBLE;
        }
        btnGetStarted.setVisibility(v);
        btnCreateAccount.setVisibility(v);
    }

    /** Opens Log in or Register. */
    private void openAuth(String mode) {
        Intent intent = new Intent(this, AuthPage.class);
        intent.putExtra(AuthPage.EXTRA_MODE, mode);
        startActivity(intent);
    }
}
