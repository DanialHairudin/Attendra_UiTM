package com.attendra.uitm.ui.session;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

/** Shared bits of the wizard pages: the shared ViewModel and the host activity. */
public abstract class BaseStepFragment extends Fragment implements WizardStep {

    protected CreateSessionViewModel vm() {
        return new ViewModelProvider(requireActivity()).get(CreateSessionViewModel.class);
    }

    protected CreateSessionActivity host() {
        return (CreateSessionActivity) requireActivity();
    }

    /** Is the bottom button allowed right now? Called on resume and after each change. */
    protected abstract boolean canContinue();

    protected void refreshContinue() {
        host().setContinueEnabled(canContinue());
    }

    @Override
    public void onResume() {
        super.onResume();
        host().onStepShown(this);
        refreshContinue();
    }
}
