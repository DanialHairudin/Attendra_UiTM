package com.attendra.uitm.ui.auth;

import android.content.Intent;
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
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.attendra.uitm.R;
import com.attendra.uitm.data.AuthJava;
import com.attendra.uitm.databinding.VerificationPageBinding;
import com.attendra.uitm.ui.settings.PreferredNamePage;
import com.attendra.uitm.ui.student.PhoneCheckPage;
import com.attendra.uitm.util.HomeRouter;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Locale;

/** Email Verification Page. */
public class VerificationPage extends Fragment {

    private static final String ARG_EMAIL = "email";
    private static final String ARG_SEND_NOW = "send_now";
    /** Set only when the code moves a student account to this phone. */
    private static final String ARG_NEW_HASH = "new_hash";
    private static final String ARG_OLD_HASH = "old_hash";
    private static final int CODE_LENGTH = 6;
    /** Redraw twice a second so the countdown never skips a number. */
    private static final long TICK_MS = 500;

    private VerificationPageBinding binding;
    private VerificationViewModel vm;
    private final AuthJava auth = new AuthJava();
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

    /** Creates the page for this email. */
    public static VerificationPage newInstance(String email, boolean sendNow) {
        Bundle args = new Bundle();
        args.putString(ARG_EMAIL, email);
        args.putBoolean(ARG_SEND_NOW, sendNow);
        VerificationPage f = new VerificationPage();
        f.setArguments(args);
        return f;
    }

    /** The same page, used to move a student account to this phone. */
    public static VerificationPage forMove(String email, String newHash,
                                              @Nullable String oldHash) {
        VerificationPage f = newInstance(email, true);
        f.requireArguments().putString(ARG_NEW_HASH, newHash);
        f.requireArguments().putString(ARG_OLD_HASH, oldHash);
        return f;
    }

    /** True if the code moves a student account to this phone. */
    private boolean isMove() {
        return requireArguments().getString(ARG_NEW_HASH) != null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = VerificationPageBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(this).get(VerificationViewModel.class);

        String email = requireArguments().getString(ARG_EMAIL, "");
        binding.txtVerifyBody.setText(bodyWithBoldEmail(email));

        boxes = new EditText[]{binding.code1, binding.code2, binding.code3,
                binding.code4, binding.code5, binding.code6};
        setUpBoxes();

        setUpButtons();

        watchViewModel();

        String uid = FirebaseAuth.getInstance().getUid();
        if (isMove() && uid != null) {
            // "Incorrect email address? Change email" is for new accounts only.
            ((View) binding.btnChangeEmail.getParent()).setVisibility(View.GONE);
            // Saved together with the code check, in one write.
            vm.setExtraUpdates(PhoneCheckPage.moveUpdates(uid,
                    requireArguments().getString(ARG_NEW_HASH),
                    requireArguments().getString(ARG_OLD_HASH)));
        }

        // After a process restart the screen is rebuilt from savedInstanceState; don't send another
        // code in that case.
        vm.start(requireArguments().getBoolean(ARG_SEND_NOW) && savedInstanceState == null);
        if (savedInstanceState == null) {
            boxes[0].requestFocus();
        }
    }

    /** What each button (and the Back button) does. */
    private void setUpButtons() {
        binding.btnVerify.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submit();
            }
        });
        binding.btnResend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                vm.resend();
            }
        });
        binding.btnChangeEmail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                auth.signOut();
                host().showLogin();
                host().showRegister();
            }
        });
        binding.btnBackToLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                leaveToLogin();
            }
        });

        // System Back also signs out, so an unverified user is never left signed in.
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        leaveToLogin();
                    }
                });
    }

    /** Updates the page when the verification state changes. */
    private void watchViewModel() {
        vm.getState().observe(getViewLifecycleOwner(), new Observer<VerificationViewModel.State>() {
            @Override
            public void onChanged(VerificationViewModel.State s) {
                render();
            }
        });
        // Code accepted: a new account has no preferred name yet, so this opens the Preferred Name
        // Page ("What should we call you?") once.
        vm.getVerifiedRole().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(String role) {
                if (role != null && isMove()) {
                    // The phone check runs again, and now it opens Home.
                    HomeRouter.openHome(requireActivity(), role);
                } else if (role != null) {
                    Intent intent = PreferredNamePage.firstTimeIntent(requireContext(), role);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    requireActivity().finish();
                }
            }
        });
        vm.getClearBoxes().observe(getViewLifecycleOwner(), new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean clear) {
                if (Boolean.TRUE.equals(clear)) {
                    clearBoxes();
                    vm.clearBoxesHandled();
                }
            }
        });
        vm.getCodeSent().observe(getViewLifecycleOwner(), new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean sent) {
                if (Boolean.TRUE.equals(sent)) {
                    Snackbar.make(binding.getRoot(), R.string.verify_sent, Snackbar.LENGTH_SHORT).show();
                    vm.codeSentHandled();
                }
            }
        });
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

    /** The six code boxes: typing, pasting and deleting. */
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
                    if (!filling) {
                        onBoxChanged(index, s.toString());
                    }
                }
            });
            // Backspace in an EMPTY box clears the box before it and moves there.
            box.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN
                            && boxes[index].length() == 0 && index > 0) {
                        setBox(index - 1, "");
                        focusBox(index - 1);
                        return true;
                    }
                    return false;
                }
            });
            box.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        if (binding.btnVerify.isEnabled()) {
                            submit();
                        }
                        return true;
                    }
                    return false;
                }
            });
        }
    }

    /** One box changed. */
    private void onBoxChanged(int index, String text) {
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.length() <= 1) {
            if (!digits.equals(text)) {
                setBox(index, digits); // Drop non-digits
            }
            if (digits.length() == 1 && index < CODE_LENGTH - 1) {
                focusBox(index + 1);
            }
        } else {
            int start;
            if (digits.length() >= CODE_LENGTH) {
                start = 0;
            } else {
                start = index;
            }
            int count = Math.min(digits.length(), CODE_LENGTH - start);
            for (int k = 0; k < count; k++) {
                setBox(start + k, String.valueOf(digits.charAt(k)));
            }
            focusBox(Math.min(start + count, CODE_LENGTH - 1));
        }
        vm.clearError();
        render();
    }

    /** Puts a digit in one box. */
    private void setBox(int index, String value) {
        filling = true;
        boxes[index].setText(value);
        boxes[index].setSelection(value.length());
        filling = false;
    }

    /** Moves the cursor to one box. */
    private void focusBox(int index) {
        boxes[index].requestFocus();
        boxes[index].selectAll();
    }

    /** Empties every box. */
    private void clearBoxes() {
        for (int i = 0; i < CODE_LENGTH; i++) {
            setBox(i, "");
        }
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

    /** Checks the code that was typed. */
    private void submit() {
        String code = enteredCode();
        if (code != null) {
            vm.verify(code);
        }
    }

    // ----------------------------------------------------------------- render

    /** Draws the whole screen from the ViewModel state and the current time. */
    private void render() {
        if (binding == null) return;
        VerificationViewModel.State s = vm.getState().getValue();
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
        if (message == null) {
            binding.txtMessage.setVisibility(View.GONE);
        } else {
            binding.txtMessage.setVisibility(View.VISIBLE);
        }
        if (message != null && !message.contentEquals(binding.txtMessage.getText())) {
            binding.txtMessage.setText(message);
        }

        // Verify button
        boolean busy = s.loading || s.sending || s.verifying;
        binding.btnVerify.setEnabled(!busy && !expired && !s.locked && !s.fatal
                && enteredCode() != null);
        if (s.verifying) {
            binding.btnVerify.setText(R.string.verify_checking);
        } else {
            binding.btnVerify.setText(R.string.verify_button);
        }
        for (EditText box : boxes) {
            box.setEnabled(!s.verifying);
        }

        renderResend(s, now);
    }

    /** Resend button: enabled, or "Resend in 25 s". */
    private void renderResend(VerificationViewModel.State s, long now) {
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

    /** Shows how long the code still works. */
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

    /** Goes back to the Log in page. */
    private void leaveToLogin() {
        auth.signOut();
        host().showLogin();
    }

    /** The body text with the email address in bold ink. */
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

    /** The Activity this page is in. */
    private AuthPage host() {
        return (AuthPage) requireActivity();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(tick);
        binding = null;
    }
}
