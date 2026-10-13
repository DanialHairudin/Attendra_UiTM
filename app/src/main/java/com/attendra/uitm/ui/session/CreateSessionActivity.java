package com.attendra.uitm.ui.session;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.attendra.uitm.R;
import com.attendra.uitm.databinding.ActivityCreateSessionBinding;
import com.attendra.uitm.ui.BaseActivity;
import com.attendra.uitm.util.HomeRouter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Create Session wizard: 1 Session Type → 2 Participants → 3 Venue → 4 Time → Review → Start. */
public class CreateSessionActivity extends BaseActivity {

    public static void open(Context from) {
        from.startActivity(new Intent(from, CreateSessionActivity.class));
    }

    private ActivityCreateSessionBinding binding;
    private CreateSessionViewModel vm;
    private View[] stepViews;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            HomeRouter.openSplash(this);
            return;
        }
        binding = ActivityCreateSessionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        padForSystemBars(binding.wizardRoot);
        stepViews = new View[]{binding.step1, binding.step2, binding.step3, binding.step4};

        vm = new ViewModelProvider(this).get(CreateSessionViewModel.class);
        vm.load(user.getUid());

        binding.btnBack.setOnClickListener(v -> goBack());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                goBack();
            }
        });
        binding.btnContinue.setOnClickListener(v -> {
            WizardStep step = currentStep();
            if (step != null) step.onContinue();
        });

        vm.getLoadState().observe(this, state -> {
            binding.txtWizardLoading.setVisibility(
                    state == CreateSessionViewModel.LoadState.READY ? View.GONE : View.VISIBLE);
            if (state == CreateSessionViewModel.LoadState.ERROR) {
                binding.txtWizardLoading.setText(vm.getLoadError());
            }
            if (state == CreateSessionViewModel.LoadState.READY
                    && getSupportFragmentManager().findFragmentById(R.id.step_container) == null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.step_container, new TypeStepFragment())
                        .commit();
            }
        });
    }

    /** Opens the next page; Back will return to the current one. */
    public void showStep(Fragment next) {
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                        android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.step_container, next)
                .addToBackStack(null)
                .commit();
    }

    /** Pages call this whenever their choices change. */
    public void setContinueEnabled(boolean enabled) {
        binding.btnContinue.setEnabled(enabled);
    }

    public void setContinueText(int textRes) {
        binding.btnContinue.setText(textRes);
    }

    private void goBack() {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();
        } else {
            finish();
        }
    }

    @Nullable
    private WizardStep currentStep() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.step_container);
        return f instanceof WizardStep ? (WizardStep) f : null;
    }

    /**
     * Each page calls this from onResume (also when Back returns to it): "New session · 2 of 4"
     * with 2 gold segments, or "Review and start" with no bar.
     */
    public void onStepShown(WizardStep step) {
        int n = step.stepNumber();
        boolean review = n > 4;
        binding.txtWizardTitle.setText(review ? R.string.review_and_start : R.string.new_session);
        binding.txtStep.setVisibility(review ? View.GONE : View.VISIBLE);
        binding.txtStep.setText(getString(R.string.step_of, n));
        binding.stepsBar.setVisibility(review ? View.GONE : View.VISIBLE);
        for (int i = 0; i < stepViews.length; i++) {
            stepViews[i].setBackgroundTintList(ContextCompat.getColorStateList(this,
                    i < n ? R.color.gold : R.color.line));
        }
        binding.btnContinue.setText(step.continueLabel());
    }
}
