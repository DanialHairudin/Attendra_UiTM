package com.attendra.uitm.ui.auth;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.attendra.uitm.R;
import com.attendra.uitm.ui.BasePage;

/** Hosts the four Auth screens as Fragments: Login, Register, Verify email and Forgot password. */
public class AuthPage extends BasePage {

    public static final String EXTRA_MODE = "mode";
    public static final String MODE_LOGIN = "login";
    public static final String MODE_REGISTER = "register";
    /** Opens the code page to move a student account to this phone (PhoneCheckPage). */
    public static final String MODE_MOVE_PHONE = "move_phone";
    public static final String EXTRA_EMAIL = "email";
    public static final String EXTRA_NEW_HASH = "new_hash";
    public static final String EXTRA_OLD_HASH = "old_hash";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.auth_page);
        padForSystemBars(findViewById(R.id.auth_root));

        // On rotation the FragmentManager restores the current screen by itself.
        if (savedInstanceState == null) {
            String mode = getIntent().getStringExtra(EXTRA_MODE);
            if (MODE_REGISTER.equals(mode)) {
                show(new RegisterPage(), false);
            } else if (MODE_MOVE_PHONE.equals(mode)) {
                show(VerificationPage.forMove(getIntent().getStringExtra(EXTRA_EMAIL),
                        getIntent().getStringExtra(EXTRA_NEW_HASH),
                        getIntent().getStringExtra(EXTRA_OLD_HASH)), false);
            } else {
                show(new LoginPage(), false);
            }
        }
    }

    /** Opens the Log in page. */
    public void showLogin() {
        clearBackStack();
        show(new LoginPage(), false);
    }

    /** Opens the Register page. */
    public void showRegister() {
        show(new RegisterPage(), true);
    }

    /** Opens the Forgot password page. */
    public void showForgotPassword() {
        show(new ForgotPasswordPage(), true);
    }

    /** Opens the Verify email page. */
    public void showVerify(String email, boolean sendNow) {
        clearBackStack();
        show(VerificationPage.newInstance(email, sendNow), false);
    }

    /** Puts this page on the screen. */
    private void show(Fragment fragment, boolean addToBackStack) {
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                        android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.auth_container, fragment);
        if (addToBackStack) {
            tx.addToBackStack(null);
        }
        tx.commit();
    }

    /** Forgets the earlier pages. */
    private void clearBackStack() {
        getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }
}
