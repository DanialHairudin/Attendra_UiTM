package com.attendra.uitm.ui.common;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.attendra.uitm.R;
import com.attendra.uitm.ui.BaseActivity;
import com.attendra.uitm.util.HomeRouter;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Parent of LecturerMainActivity and StudentMainActivity: a Fragment area on
 * top and a BottomNavigationView below. Tapping a tab replaces the Fragment.
 *
 * replace() (rather than hide/show) means only the visible tab is started, so
 * only its database listeners are attached. Each tab adds its listeners in
 * onStart() and removes them in onStop().
 */
public abstract class MainTabsActivity extends BaseActivity {

    private BottomNavigationView nav;

    /** The Fragment for a bottom-nav item ID. */
    protected abstract Fragment createTab(@IdRes int itemId);

    /** Registration.ROLE_LECTURER or ROLE_STUDENT. */
    public abstract String role();

    /** Returns false (and goes to the splash) if nobody is signed in. */
    protected boolean requireSignedIn() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            HomeRouter.openSplash(this);
            return false;
        }
        return true;
    }

    protected void setUpTabs(View content, BottomNavigationView nav,
                             @Nullable Bundle savedInstanceState) {
        this.nav = nav;
        padTopForStatusBar(content);
        nav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId());
            return true;
        });
        nav.setOnItemReselectedListener(item -> {
            // Already on this tab: nothing to do.
        });
        // After rotation or a theme change the FragmentManager and the nav
        // restore the open tab by themselves.
        if (savedInstanceState == null) showTab(R.id.nav_home);
    }

    /** Switches tab from code, e.g. "See history" on the dashboard. */
    public void selectTab(@IdRes int itemId) {
        nav.setSelectedItemId(itemId);
    }

    private void showTab(@IdRes int itemId) {
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.main_container, createTab(itemId))
                .commit();
    }

    /**
     * Pads the tab area below the status bar. The bottom nav pads itself above
     * the system navigation bar, so the insets are passed on, not consumed.
     */
    private void padTopForStatusBar(View content) {
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            return windowInsets;
        });
    }
}
