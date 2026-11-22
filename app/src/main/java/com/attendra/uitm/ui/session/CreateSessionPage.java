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
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.attendra.uitm.R;
import com.attendra.uitm.databinding.CreateSessionPageBinding;
import com.attendra.uitm.ui.BasePage;
import com.attendra.uitm.util.HomeRouter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Create Session wizard: 1 Session Type → 2 Participants → 3 Venue → 4 Time → Review → Start. */
public class CreateSessionPage extends BasePage {

    /** Opens the Create Session pages. */
    public static void open(Context from) {
        from.startActivity(new Intent(from, CreateSessionPage.class));
    }

    private CreateSessionPageBinding binding;
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
        binding = CreateSessionPageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        padForSystemBars(binding.wizardRoot);
        stepViews = new View[]{binding.step1, binding.step2, binding.step3, binding.step4};

        vm = new ViewModelProvider(this).get(CreateSessionViewModel.class);
        vm.load(user.getUid());

        binding.btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goBack();
            }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                goBack();
            }
        });
        binding.btnContinue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WizardStep step = currentStep();
                if (step != null) {
                    step.onContinue();
                }
            }
        });

        vm.getLoadState().observe(this, new Observer<CreateSessionViewModel.LoadState>() {
            @Override
            public void onChanged(CreateSessionViewModel.LoadState state) {
                if (state == CreateSessionViewModel.LoadState.READY) {
                    binding.txtWizardLoading.setVisibility(View.GONE);
                } else {
                    binding.txtWizardLoading.setVisibility(View.VISIBLE);
                }
                if (state == CreateSessionViewModel.LoadState.ERROR) {
                    binding.txtWizardLoading.setText(vm.getLoadError());
                }
                if (state == CreateSessionViewModel.LoadState.READY
                        && getSupportFragmentManager().findFragmentById(R.id.step_container) == null) {
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.step_container, new TypeStepPage())
                            .commit();
                }
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

    /** Changes the text of the Continue button. */
    public void setContinueText(int textRes) {
        binding.btnContinue.setText(textRes);
    }

    /** Back one step, or close on the first step. */
    private void goBack() {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();
        } else {
            finish();
        }
    }

    /** The step on the screen now. */
    @Nullable
    private WizardStep currentStep() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.step_container);
        if (f instanceof WizardStep) {
            return (WizardStep) f;
        }
        return null;
    }

    /**
     * Each page calls this from onResume (also when Back returns to it): "New session · 2 of 4"
     * with 2 gold segments, or "Review and start" with no bar.
     */
    public void onStepShown(WizardStep step) {
        int n = step.stepNumber();
        boolean review = n > 4;
        if (review) {
            binding.txtWizardTitle.setText(R.string.review_and_start);
            binding.txtStep.setVisibility(View.GONE);
        } else {
            binding.txtWizardTitle.setText(R.string.new_session);
            binding.txtStep.setVisibility(View.VISIBLE);
        }
        binding.txtStep.setText(getString(R.string.step_of, n));
        if (review) {
            binding.stepsBar.setVisibility(View.GONE);
        } else {
            binding.stepsBar.setVisibility(View.VISIBLE);
        }
        for (int i = 0; i < stepViews.length; i++) {
            if (i < n) {
                stepViews[i].setBackgroundTintList(ContextCompat.getColorStateList(this,
                        R.color.gold));
            } else {
                stepViews[i].setBackgroundTintList(ContextCompat.getColorStateList(this,
                        R.color.line));
            }
        }
        binding.btnContinue.setText(step.continueLabel());
    }
}
