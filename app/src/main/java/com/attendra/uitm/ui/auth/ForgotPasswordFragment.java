package com.attendra.uitm.ui.auth;

import static com.attendra.uitm.ui.auth.LoginFragment.text;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.attendra.uitm.R;
import com.attendra.uitm.data.AuthRepository;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.databinding.FragmentForgotBinding;
import com.attendra.uitm.util.Validators;
import com.google.android.material.snackbar.Snackbar;

/** "Reset password": sends a Firebase password reset link with sendPasswordResetEmail. */
public class ForgotPasswordFragment extends Fragment {

    private FragmentForgotBinding binding;
    private final AuthRepository auth = new AuthRepository();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentForgotBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.appbar.txtAppbarTitle.setText(R.string.forgot_title);
        binding.appbar.btnBack.setOnClickListener(
                v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        binding.btnSend.setOnClickListener(v -> send());
    }

    private void send() {
        String email = text(binding.inputEmail.getText()).toLowerCase();
        String error = Validators.email(email);
        binding.layoutEmail.setError(error);
        if (error != null) return;

        setSending(true);
        auth.sendPasswordReset(email, new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                if (binding == null) return;
                setSending(false);
                // Worded carefully: Firebase doesn't tell us whether the account exists.
                binding.txtSent.setText(getString(R.string.forgot_sent, email));
                binding.cardSent.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(String message) {
                if (binding == null) return;
                setSending(false);
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void setSending(boolean sending) {
        binding.btnSend.setEnabled(!sending);
        binding.btnSend.setText(sending ? R.string.forgot_sending : R.string.forgot_send);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
