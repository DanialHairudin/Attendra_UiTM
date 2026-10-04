package com.attendra.uitm.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.attendra.uitm.R;
import com.attendra.uitm.data.AuthRepository;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.databinding.FragmentLoginBinding;
import com.attendra.uitm.util.HomeRouter;
import com.attendra.uitm.util.Validators;
import com.google.android.material.snackbar.Snackbar;

/**
 * Log in with email + password (CLAUDE.md section 4):
 * - email not verified → Verify email screen
 * - verified → read users/{uid}/role → LecturerMain or StudentMain
 */
public class LoginFragment extends Fragment {

    private FragmentLoginBinding binding;
    private final AuthRepository auth = new AuthRepository();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentLoginBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.btnLogin.setOnClickListener(v -> attemptLogin());
        binding.inputPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });
        binding.btnForgot.setOnClickListener(v -> host().showForgotPassword());
        binding.btnToRegister.setOnClickListener(v -> host().showRegister());
    }

    private void attemptLogin() {
        String email = text(binding.inputEmail.getText());
        // Passwords are NOT trimmed: a space can be part of a password.
        String password = raw(binding.inputPassword.getText());

        String emailError = Validators.email(email);
        String passwordError = Validators.loginPassword(password);
        binding.layoutEmail.setError(emailError);
        binding.layoutPassword.setError(passwordError);
        if (emailError != null || passwordError != null) return;

        setLoading(true);
        auth.login(email, password, new Callback<String>() {
            @Override
            public void onSuccess(String result) {
                if (binding == null) return; // screen was closed meanwhile
                if (AuthRepository.UNVERIFIED.equals(result)) {
                    host().showVerify(email, false);
                } else {
                    HomeRouter.openHome(requireActivity(), result);
                }
            }

            @Override
            public void onError(String message) {
                if (binding == null) return;
                setLoading(false);
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        binding.btnLogin.setEnabled(!loading);
        binding.btnLogin.setText(loading ? R.string.logging_in : R.string.log_in);
        binding.inputEmail.setEnabled(!loading);
        binding.inputPassword.setEnabled(!loading);
    }

    private AuthActivity host() {
        return (AuthActivity) requireActivity();
    }

    /** Field text without leading/trailing spaces. */
    static String text(@Nullable CharSequence s) {
        return s == null ? "" : s.toString().trim();
    }

    /** Field text exactly as typed (for passwords). */
    static String raw(@Nullable CharSequence s) {
        return s == null ? "" : s.toString();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
