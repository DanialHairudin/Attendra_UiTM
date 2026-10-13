package com.attendra.uitm.ui.session;

import androidx.annotation.StringRes;

/** One page of the Create Session wizard. */
public interface WizardStep {

    /** 1–4 for the numbered steps, 5 for the Review page (no step bar). */
    int stepNumber();

    /** Text of the big bottom button ("Continue", "Review", "Start session"). */
    @StringRes
    int continueLabel();

    /** The bottom button was tapped. */
    void onContinue();
}
