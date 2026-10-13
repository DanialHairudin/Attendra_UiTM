package com.attendra.uitm.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.annotation.StringRes;

import com.attendra.uitm.R;

/** Text size chosen in Settings: Small, Default, Large or Extra large. */
public final class TextSizePrefs {

    public static final int SMALL = 0;
    public static final int DEFAULT = 1;
    public static final int LARGE = 2;
    public static final int EXTRA_LARGE = 3;

    private static final float[] SCALES = {0.9f, 1.0f, 1.15f, 1.3f};
    private static final int[] LABELS = {R.string.text_size_small, R.string.text_size_default,
            R.string.text_size_large, R.string.text_size_extra_large};

    private static final String PREFS_NAME = "attendra_prefs";
    private static final String KEY_TEXT_SIZE = "text_size";

    private TextSizePrefs() {
    }

    /** The saved level (0–3), or DEFAULT if never changed. */
    public static int get(Context context) {
        int level = prefs(context).getInt(KEY_TEXT_SIZE, DEFAULT);
        return level < SMALL || level > EXTRA_LARGE ? DEFAULT : level;
    }

    public static void save(Context context, int level) {
        prefs(context).edit().putInt(KEY_TEXT_SIZE, level).apply();
    }

    public static float scale(int level) {
        return SCALES[level];
    }

    @StringRes
    public static int label(int level) {
        return LABELS[level];
    }

    /** A copy of the context whose font scale includes our factor. */
    public static Context wrap(Context base) {
        float factor = scale(get(base));
        if (factor == 1.0f) return base;
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.fontScale = config.fontScale * factor;
        return base.createConfigurationContext(config);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
