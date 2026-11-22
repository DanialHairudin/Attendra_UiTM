package com.attendra.uitm.ui.auth;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.attendra.uitm.R;
import com.attendra.uitm.data.AuthJava;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.databinding.LoginPageBinding;
import com.attendra.uitm.databinding.SheetTesterBinding;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.util.HomeRouter;
import com.attendra.uitm.util.TestAccounts;
import com.attendra.uitm.util.Validators;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;

/** Log in with email + password. */
public class LoginPage extends Fragment {

    /** How long the logo must be held to open the tester panel. */
    private static final long TESTER_HOLD_MS = 2_000;

    private LoginPageBinding binding;
    private final AuthJava auth = new AuthJava();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = LoginPageBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptLogin();
            }
        });
        binding.inputPassword.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });
        binding.btnForgot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host().showForgotPassword();
            }
        });
        binding.btnToRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host().showRegister();
            }
        });

        // Hidden tester panel.
        if (TestAccounts.enabled()) {
            setUpTesterPanel();
        }
    }

    // ------------------------------------------------- tester panel (DEBUG only)

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable openTesterPanel = new Runnable() {
        @Override
        public void run() {
            showTesterPanel();
        }
    };

    /** Holding the logo for TESTER_HOLD_MS opens "Tester access". */
    @SuppressLint("ClickableViewAccessibility") // Hidden debug-only gesture, not a real control
    private void setUpTesterPanel() {
        binding.imgLogo.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
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
            }
        });
    }

    /** Opens the tester panel (debug builds only). */
    private void showTesterPanel() {
        if (binding == null) return;
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        SheetTesterBinding sheetBinding = SheetTesterBinding.inflate(getLayoutInflater());
        sheetBinding.btnTestLecturer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sheet.dismiss();
                loginAsTester(Registration.ROLE_LECTURER);
            }
        });
        sheetBinding.btnTestStudent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sheet.dismiss();
                loginAsTester(Registration.ROLE_STUDENT);
            }
        });
        sheet.setContentView(sheetBinding.getRoot());
        sheet.show();
    }

    /** Logs in with a tester account. */
    private void loginAsTester(String role) {
        if (!TestAccounts.available(role)) {
            Snackbar.make(binding.getRoot(), R.string.tester_missing, Snackbar.LENGTH_LONG).show();
            return;
        }
        binding.layoutEmail.setError(null);
        binding.layoutPassword.setError(null);
        login(TestAccounts.email(role), TestAccounts.password(role));
    }

    // ============================================================
    // [LOGIN] Check the email and password boxes first
    // ============================================================
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

    // ============================================================
    // [LOGIN] Sign in, then open the right screen
    // ============================================================
    /**
     * Signs in and routes: Verify screen if unverified, the Preferred Name Page if no name was
     * picked yet, otherwise the role's dashboard.
     */
    private void login(String email, String password) {
        setLoading(true);
        auth.login(email, password, new Callback<AuthJava.Route>() {
            @Override
            public void onSuccess(AuthJava.Route route) {
                if (binding == null) return; // Screen was closed meanwhile
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

    /** Turns the form off while logging in. */
    private void setLoading(boolean loading) {
        binding.btnLogin.setEnabled(!loading);
        if (loading) {
            binding.btnLogin.setText(R.string.logging_in);
        } else {
            binding.btnLogin.setText(R.string.log_in);
        }
        binding.inputEmail.setEnabled(!loading);
        binding.inputPassword.setEnabled(!loading);
    }

    /** The Activity this page is in. */
    private AuthPage host() {
        return (AuthPage) requireActivity();
    }

    /** Field text without leading/trailing spaces. */
    static String text(@Nullable CharSequence s) {
        if (s == null) {
            return "";
        }
        return s.toString().trim();
    }

    /** Field text exactly as typed (for passwords). */
    static String raw(@Nullable CharSequence s) {
        if (s == null) {
            return "";
        }
        return s.toString();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(openTesterPanel);
        binding = null;
    }
}
