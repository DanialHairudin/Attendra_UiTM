package com.attendra.uitm.ui.common;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/** Swipe a list row left or right to dismiss it. */
public final class SwipeToDismiss {

    private SwipeToDismiss() {
    }

    @SuppressLint("ClickableViewAccessibility") // Tap and long-press are still handled by the row
    public static void attach(View row, Runnable onDismiss) {
        int slop = ViewConfiguration.get(row.getContext()).getScaledTouchSlop();
        float[] down = new float[2];
        boolean[] swiping = {false};

        row.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = e.getRawX();
                    down[1] = e.getRawY();
                    swiping[0] = false;
                    return false; // Let the row see the press (ripple, click, long-press)

                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - down[0];
                    float dy = e.getRawY() - down[1];
                    if (!swiping[0] && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                        swiping[0] = true;
                        // Stop the ScrollView from scrolling, and cancel the row's press.
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        MotionEvent cancel = MotionEvent.obtain(e);
                        cancel.setAction(MotionEvent.ACTION_CANCEL);
                        v.onTouchEvent(cancel);
                        cancel.recycle();
                    }
                    if (!swiping[0]) return false;
                    v.setTranslationX(dx);
                    v.setAlpha(1f - Math.min(1f, Math.abs(dx) / v.getWidth()));
                    return true;
                }

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    if (!swiping[0]) return false;
                    swiping[0] = false;
                    float dx = v.getTranslationX();
                    if (e.getActionMasked() == MotionEvent.ACTION_UP
                            && Math.abs(dx) > v.getWidth() / 3f) {
                        v.animate().translationX(Math.signum(dx) * v.getWidth()).alpha(0f)
                                .setDuration(150).withEndAction(onDismiss).start();
                    } else {
                        v.animate().translationX(0f).alpha(1f).setDuration(150).start();
                    }
                    return true;
                }

                default:
                    return false;
            }
        });
    }
}
