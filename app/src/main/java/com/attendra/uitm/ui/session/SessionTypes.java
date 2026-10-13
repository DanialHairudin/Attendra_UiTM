package com.attendra.uitm.ui.session;

import androidx.annotation.StringRes;

import com.attendra.uitm.R;
import com.attendra.uitm.model.Session;

/** Display names for the four session types. */
public final class SessionTypes {

    private SessionTypes() {
    }

    /** "Normal class", "Test", "SULAM / outside", "Club activity". */
    @StringRes
    public static int name(String type) {
        if (Session.TYPE_TEST.equals(type)) return R.string.type_test;
        if (Session.TYPE_SULAM.equals(type)) return R.string.type_sulam;
        if (Session.TYPE_CLUB.equals(type)) return R.string.type_club;
        return R.string.type_class;
    }

    /** Suggested session title, e.g. "%1$s class" → "ICT602 class". */
    @StringRes
    public static int titlePattern(String type) {
        if (Session.TYPE_TEST.equals(type)) return R.string.title_pattern_test;
        if (Session.TYPE_SULAM.equals(type)) return R.string.title_pattern_sulam;
        if (Session.TYPE_CLUB.equals(type)) return R.string.title_pattern_club;
        return R.string.title_pattern_class;
    }
}
