package com.attendra.uitm.ui.common;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.attendra.uitm.R;
import com.attendra.uitm.databinding.FragmentPlaceholderTabBinding;

/**
 * A bottom-nav tab whose real screen comes in a later build step
 * (History C11, My attendance C10, Notifications C8). It shows an Empty State
 * Page style message instead of made-up data.
 */
public class PlaceholderTabFragment extends Fragment {

    private static final String ARG_HEADING = "heading";
    private static final String ARG_ICON = "icon";
    private static final String ARG_BODY = "body";

    public static PlaceholderTabFragment newInstance(@StringRes int heading, @DrawableRes int icon,
                                                     @StringRes int body) {
        Bundle args = new Bundle();
        args.putInt(ARG_HEADING, heading);
        args.putInt(ARG_ICON, icon);
        args.putInt(ARG_BODY, body);
        PlaceholderTabFragment f = new PlaceholderTabFragment();
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        FragmentPlaceholderTabBinding b =
                FragmentPlaceholderTabBinding.inflate(inflater, container, false);
        Bundle args = requireArguments();
        b.placeholderHeading.setText(args.getInt(ARG_HEADING));
        b.placeholderIcon.setImageResource(args.getInt(ARG_ICON));
        b.placeholderTitle.setText(R.string.coming_soon);
        b.placeholderBody.setText(args.getInt(ARG_BODY));
        return b.getRoot();
    }
}
