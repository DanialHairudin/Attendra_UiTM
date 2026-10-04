package com.attendra.uitm.ui;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Parent of every Activity in the app.
 *
 * Android 15+ always draws apps edge-to-edge (behind the status bar and the
 * navigation bar). EdgeToEdge.enable() does the same on older phones and sets
 * dark or light system bar icons to match the theme. padForSystemBars() then
 * pushes the screen content out from under those bars.
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
    }

    /**
     * Adds the status bar / nav bar height to the view's own padding.
     * The keyboard (ime) is included too: in edge-to-edge mode adjustResize no
     * longer shrinks the window, so without this the keyboard would cover the
     * bottom of forms.
     */
    protected void padForSystemBars(View root) {
        final int left = root.getPaddingLeft();
        final int top = root.getPaddingTop();
        final int right = root.getPaddingRight();
        final int bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(left + bars.left, top + bars.top,
                    right + bars.right, bottom + bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
