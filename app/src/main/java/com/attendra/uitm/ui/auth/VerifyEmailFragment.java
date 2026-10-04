package com.attendra.uitm.ui.auth;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.attendra.uitm.R;
import com.attendra.uitm.data.AuthRepository;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.databinding.FragmentVerifyBinding;
import com.attendra.uitm.util.HomeRouter;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseUser;

/**
 * "Check your inbox" screen (CLAUDE.md section 4, step 4).
 *
 * The unverified user stays signed in ONLY while this screen is open, because
 * Resend and "I've verified" both need the signed-in FirebaseUser. Leaving the
 * screen (Back, "Back to log in", "Change it") signs them out. SplashActivity also
 * signs out any unverified user it finds, e.g. if the app was closed here.
 */
public class VerifyEmailFragment extends Fragment {

    private static final String ARG_EMAIL = "email";
    private static final String ARG_JUST_SENT = "just_sent";
    private static final String STATE_COOLDOWN_END = "cooldown_end";
    private static final long COOLDOWN_MS = 60_000;

    private FragmentVerifyBinding binding;
    private final AuthRepository auth = new AuthRepository();
    private CountDownTimer timer;
    /** When the Resend cooldown ends, in SystemClock.elapsedRealtime() time. 0 = no cooldown. */
    private long cooldownEnd;

    public static VerifyEmailFragment newInstance(String email, boolean justSent) {
        Bundle args = new Bundle();
        args.putString(ARG_EMAIL, email);
        args.putBoolean(ARG_JUST_SENT, justSent);
        VerifyEmailFragment f = new VerifyEmailFragment();
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentVerifyBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        String email = requireArguments().getString(ARG_EMAIL, "");
        binding.txtVerifyBody.setText(bodyWithBoldEmail(email));

        binding.btnVerified.setOnClickListener(v -> checkVerified());
        binding.btnResend.setOnClickListener(v -> resend());
        binding.btnChangeEmail.setOnClickListener(v -> {
            auth.signOut();
            host().showLogin();
            host().showRegister();
        });
        binding.btnBackToLogin.setOnClickListener(v -> leaveToLogin());

        // System Back also signs out, so an unverified user is never left signed in.
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        leaveToLogin();
                    }
                });

        // Cooldown: restored after rotation, or started fresh if the email was just sent.
        if (savedInstanceState != null) {
            cooldownEnd = savedInstanceState.getLong(STATE_COOLDOWN_END, 0);
        } else if (requireArguments().getBoolean(ARG_JUST_SENT)) {
            cooldownEnd = SystemClock.elapsedRealtime() + COOLDOWN_MS;
        }
        startCooldownTimer();
    }

    /** "I've verified": reload the user from Firebase, then check isEmailVerified(). */
    private void checkVerified() {
        setChecking(true);
        auth.reloadAndCheckVerified(new Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean verified) {
                if (binding == null) return;
                FirebaseUser user = auth.currentUser();
                if (!verified || user == null) {
                    setChecking(false);
                    Snackbar.make(binding.getRoot(), R.string.verify_not_yet, Snackbar.LENGTH_LONG).show();
                    return;
                }
                auth.loadRole(user, new Callback<String>() {
                    @Override
                    public void onSuccess(String role) {
                        if (binding == null) return;
                        HomeRouter.openHome(requireActivity(), role);
                    }

                    @Override
                    public void onError(String message) {
                        showError(message);
                    }
                });
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });
    }

    private void resend() {
        binding.btnResend.setEnabled(false);
        auth.resendVerification(new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                if (binding == null) return;
                Snackbar.make(binding.getRoot(), R.string.verify_resent, Snackbar.LENGTH_SHORT).show();
                cooldownEnd = SystemClock.elapsedRealtime() + COOLDOWN_MS;
                startCooldownTimer();
            }

            @Override
            public void onError(String message) {
                if (binding == null) return;
                binding.btnResend.setEnabled(true);
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    /** Counts down on the Resend button ("Resend in 42s") until the cooldown ends. */
    private void startCooldownTimer() {
        if (timer != null) timer.cancel();
        long remaining = cooldownEnd - SystemClock.elapsedRealtime();
        if (remaining <= 0) {
            cooldownEnd = 0;
            binding.btnResend.setEnabled(true);
            binding.btnResend.setText(R.string.verify_resend);
            return;
        }
        binding.btnResend.setEnabled(false);
        timer = new CountDownTimer(remaining, 1000) {
            @Override
            public void onTick(long millisLeft) {
                if (binding == null) return;
                int seconds = (int) Math.ceil(millisLeft / 1000.0);
                binding.btnResend.setText(getString(R.string.verify_resend_wait, seconds));
            }

            @Override
            public void onFinish() {
                if (binding == null) return;
                cooldownEnd = 0;
                binding.btnResend.setEnabled(true);
                binding.btnResend.setText(R.string.verify_resend);
            }
        }.start();
    }

    private void leaveToLogin() {
        auth.signOut();
        host().showLogin();
    }

    private void setChecking(boolean checking) {
        binding.btnVerified.setEnabled(!checking);
        binding.btnVerified.setText(checking ? R.string.verify_checking : R.string.verify_done);
    }

    private void showError(String message) {
        if (binding == null) return;
        setChecking(false);
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }

    /** The body text with the email address in bold ink, like the prototype. */
    private CharSequence bodyWithBoldEmail(String email) {
        String full = getString(R.string.verify_body, email);
        SpannableString s = new SpannableString(full);
        int start = full.indexOf(email);
        if (start >= 0 && !email.isEmpty()) {
            int end = start + email.length();
            s.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.ink)),
                    start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return s;
    }

    private AuthActivity host() {
        return (AuthActivity) requireActivity();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(STATE_COOLDOWN_END, cooldownEnd);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (timer != null) timer.cancel();
        binding = null;
    }
}
