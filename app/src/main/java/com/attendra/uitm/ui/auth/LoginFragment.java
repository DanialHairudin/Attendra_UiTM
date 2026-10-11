package com.attendra.uitm.ui.auth;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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
import com.attendra.uitm.databinding.SheetTesterBinding;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.util.HomeRouter;
import com.attendra.uitm.util.TestAccounts;
import com.attendra.uitm.util.Validators;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;

/**
 * Log in with email + password (CLAUDE.md section 4):
 * - email not verified → Verify email screen
 * - verified → read roles/{uid} → LecturerMain or StudentMain
 *
 * DEBUG builds also have the hidden "Tester access" panel (CLAUDE.md 12.4).
 */
public class LoginFragment extends Fragment {

    /** How long the logo must be held to open the tester panel. */
    private static final long TESTER_HOLD_MS = 2_000;

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

        // Hidden tester panel. In a release build this line is skipped, so the
        // logo has no touch handling at all and nothing hints that it exists.
        if (TestAccounts.enabled()) setUpTesterPanel();
    }

    // ------------------------------------------------- tester panel (DEBUG only)

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable openTesterPanel = this::showTesterPanel;

    /**
     * Holding the logo for TESTER_HOLD_MS opens "Tester access". Android's normal
     * long-press fires after about half a second, which is too easy to trigger
     * by accident, so the 2 s timer is done by hand: start it when the finger
     * goes down, cancel it when the finger lifts (or the screen scrolls).
     */
    @SuppressLint("ClickableViewAccessibility") // hidden debug-only gesture, not a real control
    private void setUpTesterPanel() {
        binding.imgLogo.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    handler.postDelayed(openTesterPanel, TESTER_HOLD_MS);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(openTesterPanel);
                    return true;
                default:
                    return true;
            }
        });
    }

    private void showTesterPanel() {
        if (binding == null) return;
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        SheetTesterBinding sheetBinding = SheetTesterBinding.inflate(getLayoutInflater());
        sheetBinding.btnTestLecturer.setOnClickListener(v -> {
            sheet.dismiss();
            loginAsTester(Registration.ROLE_LECTURER);
        });
        sheetBinding.btnTestStudent.setOnClickListener(v -> {
            sheet.dismiss();
            loginAsTester(Registration.ROLE_STUDENT);
        });
        sheet.setContentView(sheetBinding.getRoot());
        sheet.show();
    }

    private void loginAsTester(String role) {
        if (!TestAccounts.available(role)) {
            Snackbar.make(binding.getRoot(), R.string.tester_missing, Snackbar.LENGTH_LONG).show();
            return;
        }
        binding.layoutEmail.setError(null);
        binding.layoutPassword.setError(null);
        login(TestAccounts.email(role), TestAccounts.password(role));
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

        login(email, password);
    }

    /**
     * Signs in and routes: Verify screen if unverified, the Preferred Name Page if
     * no name was picked yet, otherwise the role's dashboard.
     */
    private void login(String email, String password) {
        setLoading(true);
        auth.login(email, password, new Callback<AuthRepository.Route>() {
            @Override
            public void onSuccess(AuthRepository.Route route) {
                if (binding == null) return; // screen was closed meanwhile
                if (route.unverified) {
                    host().showVerify(email, false);
                } else {
                    HomeRouter.open(requireActivity(), route);
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
        handler.removeCallbacks(openTesterPanel);
        binding = null;
    }
}
