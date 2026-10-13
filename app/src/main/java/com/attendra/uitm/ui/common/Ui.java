package com.attendra.uitm.ui.common;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.attendra.uitm.R;
import com.attendra.uitm.databinding.ViewEmptyBinding;

import java.util.Calendar;

/** Small helpers shared by the dashboard, Classes, Settings and Profile screens. */
public final class Ui {

    private Ui() {
    }

    /** The three states of a list card. */
    public enum ListState { LOADING, EMPTY, CONTENT }

    /** Shows one of: the "Loading…" text, the empty-state message, or the list. */
    public static void showListState(ListState state, View loading, ViewEmptyBinding empty,
                                     View list) {
        loading.setVisibility(state == ListState.LOADING ? View.VISIBLE : View.GONE);
        empty.getRoot().setVisibility(state == ListState.EMPTY ? View.VISIBLE : View.GONE);
        list.setVisibility(state == ListState.CONTENT ? View.VISIBLE : View.GONE);
    }

    /** Fills the "Empty State Page" style message inside a card. */
    public static void setEmpty(ViewEmptyBinding empty, @DrawableRes int icon,
                                @StringRes int title, @StringRes int body) {
        empty.emptyIcon.setImageResource(icon);
        empty.emptyTitle.setText(title);
        empty.emptyBody.setText(body);
    }

    /** Adds a row to a list card. */
    public static void addRow(LinearLayout list, View row) {
        if (list.getChildCount() > 0) {
            View line = new View(list.getContext());
            line.setBackgroundColor(ContextCompat.getColor(list.getContext(), R.color.line));
            list.addView(line, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(list.getContext(), 1)));
        }
        list.addView(row);
    }

    /** A gold badge (e.g. a club's short name) for a horizontal row of badges. */
    public static TextView goldBadge(LinearLayout parent, CharSequence text) {
        TextView badge = (TextView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_badge_gold, parent, false);
        badge.setText(text);
        if (parent.getChildCount() > 0) {
            ((LinearLayout.LayoutParams) badge.getLayoutParams()).setMarginStart(
                    dp(parent.getContext(), 8));
        }
        parent.addView(badge);
        return badge;
    }

    /** Colours a badge: text in `color`, background in the matching 12% tint. */
    public static void tintBadge(TextView badge, @ColorRes int color, @ColorRes int tint) {
        Context c = badge.getContext();
        badge.setTextColor(ContextCompat.getColor(c, color));
        badge.setBackgroundTintList(ContextCompat.getColorStateList(c, tint));
    }

    /** "Good morning/afternoon/evening, {name}" from the phone's current hour. */
    public static String greeting(Context c, String name, long now) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int res = hour < 12 ? R.string.greeting_morning
                : hour < 18 ? R.string.greeting_afternoon
                : R.string.greeting_evening;
        return c.getString(res, name);
    }

    public static int dp(Context c, int dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }
}
