package com.attendra.uitm.ui.common;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.attendra.uitm.databinding.ActivityComingSoonBinding;
import com.attendra.uitm.ui.BaseActivity;

/**
 * Stand-in screen for buttons whose real screen comes in a later build step
 * (Create session C6, Live monitor C7, Check in C9, Session report C11).
 */
public class ComingSoonActivity extends BaseActivity {

    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_BODY = "body";

    public static void open(Context from, @StringRes int title, @StringRes int body) {
        Intent intent = new Intent(from, ComingSoonActivity.class);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_BODY, body);
        from.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityComingSoonBinding binding = ActivityComingSoonBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        padForSystemBars(binding.comingRoot);

        binding.comingTitle.setText(getIntent().getIntExtra(EXTRA_TITLE, 0));
        binding.comingBody.setText(getIntent().getIntExtra(EXTRA_BODY, 0));
        binding.btnComingBack.setOnClickListener(v -> finish());
    }
}
