package com.attendra.uitm.ui.auth;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.attendra.uitm.R;
import com.attendra.uitm.ui.BaseActivity;

/** Hosts the four Auth screens as Fragments: Login, Register, Verify email and Forgot password. */
public class AuthActivity extends BaseActivity {

    public static final String EXTRA_MODE = "mode";
    public static final String MODE_LOGIN = "login";
    public static final String MODE_REGISTER = "register";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
        padForSystemBars(findViewById(R.id.auth_root));

        // On rotation the FragmentManager restores the current screen by itself.
        if (savedInstanceState == null) {
            boolean register = MODE_REGISTER.equals(getIntent().getStringExtra(EXTRA_MODE));
            show(register ? new RegisterFragment() : new LoginFragment(), false);
        }
    }

    public void showLogin() {
        clearBackStack();
        show(new LoginFragment(), false);
    }

    public void showRegister() {
        show(new RegisterFragment(), true);
    }

    public void showForgotPassword() {
        show(new ForgotPasswordFragment(), true);
    }

    public void showVerify(String email, boolean sendNow) {
        clearBackStack();
        show(VerifyEmailFragment.newInstance(email, sendNow), false);
    }

    private void show(Fragment fragment, boolean addToBackStack) {
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                        android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.auth_container, fragment);
        if (addToBackStack) tx.addToBackStack(null);
        tx.commit();
    }

    private void clearBackStack() {
        getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }
}
