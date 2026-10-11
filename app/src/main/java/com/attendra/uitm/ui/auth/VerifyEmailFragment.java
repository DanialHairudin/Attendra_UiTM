package com.attendra.uitm.ui.auth;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.attendra.uitm.R;
import com.attendra.uitm.data.AuthRepository;
import com.attendra.uitm.databinding.FragmentVerifyBinding;
import com.attendra.uitm.util.HomeRouter;
import com.google.android.material.snackbar.Snackbar;

import java.util.Locale;

/**
 * Email Verification Page (CLAUDE.md 12.3).
 *
 * The user types the 6-digit code from the email into six boxes. The boxes:
 * - move to the next box after each digit (auto-advance),
 * - go back one box on Backspace in an empty box,
 * - fill all six when a 6-digit code is pasted into any box.
 *
 * "Code expires in 0:59" counts down; at 0 Verify is disabled. Resend code is
 * disabled for 30 s after each send. All the logic about codes lives in
 * VerifyViewModel / VerificationRepository; this class only draws the state.
 *
 * The unverified user stays signed in ONLY while this screen is open, because
 * saving the code and setting emailVerified need the signed-in user. Leaving the
 * screen (Back, "Return to log in", "Change email") signs them out. SplashActivity
 * also signs out any unverified user it finds, e.g. if the app was closed here.
 */
public class VerifyEmailFragment extends Fragment {

    private static final String ARG_EMAIL = "email";
    private static final String ARG_SEND_NOW = "send_now";
    private static final int CODE_LENGTH = 6;
    /** Redraw twice a second so the countdown never skips a number. */
    private static final long TICK_MS = 500;

    private FragmentVerifyBinding binding;
    private VerifyViewModel vm;
    private final AuthRepository auth = new AuthRepository();
    private EditText[] boxes;
    /** True while the code changes box text itself, so the TextWatchers ignore it. */
    private boolean filling;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, TICK_MS);
        }
    };

    /**
     * @param sendNow true straight after registering (the first code is sent when
     *                the screen opens); false when coming from Login (no code is
     *                sent until the user taps Resend code).
     */
    public static VerifyEmailFragment newInstance(String email, boolean sendNow) {
        Bundle args = new Bundle();
        args.putString(ARG_EMAIL, email);
        args.putBoolean(ARG_SEND_NOW, sendNow);
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
        vm = new ViewModelProvider(this).get(VerifyViewModel.class);

        String email = requireArguments().getString(ARG_EMAIL, "");
        binding.txtVerifyBody.setText(bodyWithBoldEmail(email));

        boxes = new EditText[]{binding.code1, binding.code2, binding.code3,
                binding.code4, binding.code5, binding.code6};
        setUpBoxes();

        binding.btnVerify.setOnClickListener(v -> submit());
        binding.btnResend.setOnClickListener(v -> vm.resend());
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

        vm.getState().observe(getViewLifecycleOwner(), s -> render());
        vm.getVerifiedRole().observe(getViewLifecycleOwner(), role -> {
            if (role != null) HomeRouter.openHome(requireActivity(), role);
        });
        vm.getClearBoxes().observe(getViewLifecycleOwner(), clear -> {
            if (Boolean.TRUE.equals(clear)) {
                clearBoxes();
                vm.clearBoxesHandled();
            }
        });
        vm.getCodeSent().observe(getViewLifecycleOwner(), sent -> {
            if (Boolean.TRUE.equals(sent)) {
                Snackbar.make(binding.getRoot(), R.string.verify_sent, Snackbar.LENGTH_SHORT).show();
                vm.codeSentHandled();
            }
        });

        // After a process restart the screen is rebuilt from savedInstanceState;
        // don't send another code in that case.
        vm.start(requireArguments().getBoolean(ARG_SEND_NOW) && savedInstanceState == null);
        if (savedInstanceState == null) boxes[0].requestFocus();
    }

    @Override
    public void onStart() {
        super.onStart();
        handler.post(tick);
    }

    @Override
    public void onStop() {
        super.onStop();
        handler.removeCallbacks(tick);
    }

    // ------------------------------------------------------------- code boxes

    private void setUpBoxes() {
        for (int i = 0; i < CODE_LENGTH; i++) {
            final int index = i;
            EditText box = boxes[i];
            box.setContentDescription(getString(R.string.verify_digit_description, i + 1));
            // Tapping a filled box selects its digit, so typing replaces it.
            box.setSelectAllOnFocus(true);
            box.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (!filling) onBoxChanged(index, s.toString());
                }
            });
            // Backspace in an EMPTY box clears the box before it and moves there.
            box.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN
                        && boxes[index].length() == 0 && index > 0) {
                    setBox(index - 1, "");
                    focusBox(index - 1);
                    return true;
                }
                return false;
            });
            box.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    if (binding.btnVerify.isEnabled()) submit();
                    return true;
                }
                return false;
            });
        }
    }

    /**
     * One box changed. Normally it holds one digit, and focus moves on.
     * If it holds more than one digit, a code was pasted: the digits are spread
     * over the boxes (a full 6-digit code always starts at the first box).
     */
    private void onBoxChanged(int index, String text) {
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.length() <= 1) {
            if (!digits.equals(text)) setBox(index, digits); // drop non-digits
            if (digits.length() == 1 && index < CODE_LENGTH - 1) focusBox(index + 1);
        } else {
            int start = digits.length() >= CODE_LENGTH ? 0 : index;
            int count = Math.min(digits.length(), CODE_LENGTH - start);
            for (int k = 0; k < count; k++) {
                setBox(start + k, String.valueOf(digits.charAt(k)));
            }
            focusBox(Math.min(start + count, CODE_LENGTH - 1));
        }
        vm.clearError();
        render();
    }

    private void setBox(int index, String value) {
        filling = true;
        boxes[index].setText(value);
        boxes[index].setSelection(value.length());
        filling = false;
    }

    private void focusBox(int index) {
        boxes[index].requestFocus();
        boxes[index].selectAll();
    }

    private void clearBoxes() {
        for (int i = 0; i < CODE_LENGTH; i++) setBox(i, "");
        focusBox(0);
    }

    /** The typed code, or null if not all six boxes have a digit. */
    @Nullable
    private String enteredCode() {
        StringBuilder code = new StringBuilder();
        for (EditText box : boxes) {
            if (box.length() != 1) return null;
            code.append(box.getText());
        }
        return code.toString();
    }

    private void submit() {
        String code = enteredCode();
        if (code != null) vm.verify(code);
    }

    // ----------------------------------------------------------------- render

    /** Draws the whole screen from the ViewModel state and the current time. */
    private void render() {
        if (binding == null) return;
        VerifyViewModel.State s = vm.getState().getValue();
        if (s == null) return;
        long now = System.currentTimeMillis();
        boolean expired = !s.loading && !s.sending && now >= s.expiresAt;

        // Countdown line
        if (s.loading) {
            showCountdown(getString(R.string.verify_loading));
        } else if (s.sending) {
            showCountdown(getString(R.string.verify_sending));
        } else if (expired || s.fatal) {
            binding.txtCountdown.setVisibility(View.GONE);
        } else {
            showCountdown(getString(R.string.code_expires_in, minSec(s.expiresAt - now)));
        }

        // Message line, most important first
        String message = null;
        if (s.fatal) {
            message = s.error;
        } else if (s.sendFailed) {
            message = getString(R.string.code_send_failed);
        } else if (s.locked) {
            message = getString(R.string.code_too_many);
        } else if (expired) {
            message = getString(R.string.code_expired);
        } else if (s.error != null) {
            message = s.error;
        }
        binding.txtMessage.setVisibility(message == null ? View.GONE : View.VISIBLE);
        if (message != null && !message.contentEquals(binding.txtMessage.getText())) {
            binding.txtMessage.setText(message);
        }

        // Verify button
        boolean busy = s.loading || s.sending || s.verifying;
        binding.btnVerify.setEnabled(!busy && !expired && !s.locked && !s.fatal
                && enteredCode() != null);
        binding.btnVerify.setText(s.verifying ? R.string.verify_checking : R.string.verify_button);
        for (EditText box : boxes) box.setEnabled(!s.verifying);

        // Resend code (30 s cooldown after each send)
        long wait = s.resendAt - now;
        if (wait > 0 && !s.loading && !s.sending) {
            binding.btnResend.setEnabled(false);
            binding.btnResend.setText(getString(R.string.verify_resend_wait, minSec(wait)));
        } else {
            binding.btnResend.setEnabled(!s.loading && !s.sending && !s.fatal);
            binding.btnResend.setText(R.string.verify_resend);
        }
    }

    private void showCountdown(String text) {
        binding.txtCountdown.setVisibility(View.VISIBLE);
        binding.txtCountdown.setText(text);
    }

    /** Milliseconds as "m:ss", rounded up, so 59.2 s shows as 1:00 → 0:59 → … → 0:01. */
    private static String minSec(long ms) {
        long seconds = Math.max(0, (ms + 999) / 1000);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    // ----------------------------------------------------------------- helpers

    private void leaveToLogin() {
        auth.signOut();
        host().showLogin();
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
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(tick);
        binding = null;
    }
}
