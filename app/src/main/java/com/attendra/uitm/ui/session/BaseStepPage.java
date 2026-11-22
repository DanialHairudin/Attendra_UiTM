package com.attendra.uitm.ui.session;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

/** Shared bits of the wizard pages: the shared ViewModel and the host activity. */
public abstract class BaseStepPage extends Fragment implements WizardStep {

    /** The choices made so far. */
    protected CreateSessionViewModel vm() {
        return new ViewModelProvider(requireActivity()).get(CreateSessionViewModel.class);
    }

    /** The Create Session page this step is in. */
    protected CreateSessionPage host() {
        return (CreateSessionPage) requireActivity();
    }

    /** Is the bottom button allowed right now? */
    protected abstract boolean canContinue();

    /** Turns the Continue button on or off. */
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
