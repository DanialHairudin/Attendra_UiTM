package com.attendra.uitm.ui.common;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.attendra.uitm.R;
import com.attendra.uitm.ui.BasePage;
import com.attendra.uitm.util.HomeRouter;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Parent of LecturerMainPage and StudentMainPage: a Fragment area on top and a
 * BottomNavigationView below.
 */
public abstract class MainTabsPage extends BasePage {

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

    /** Shows the first tab and what each tab opens. */
    protected void setUpTabs(View content, BottomNavigationView nav,
                             @Nullable Bundle savedInstanceState) {
        this.nav = nav;
        padTopForStatusBar(findViewById(R.id.top_bar));
        nav.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                showTab(item.getItemId());
                return true;
            }
        });
        if (savedInstanceState != null) {
            setTopTitle(nav.getSelectedItemId());
        }
        nav.setOnItemReselectedListener(new NavigationBarView.OnItemReselectedListener() {
            @Override
            public void onNavigationItemReselected(MenuItem item) {
                // Already on this tab: nothing to do.
            }
        });
        // After rotation or a theme change the FragmentManager and the nav restore the open tab by
        // themselves.
        if (savedInstanceState == null) {
            showTab(R.id.nav_home);
        }
    }

    /** Switches tab from code, e.g. "See history" on the dashboard. */
    public void selectTab(@IdRes int itemId) {
        nav.setSelectedItemId(itemId);
    }

    /** Shows one tab. */
    private void showTab(@IdRes int itemId) {
        setTopTitle(itemId);
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.main_container, createTab(itemId))
                .commit();
    }

    /** Top bar title: the app name on Home, otherwise the tab's name ("Settings"). */
    private void setTopTitle(@IdRes int itemId) {
        TextView title = findViewById(R.id.txt_top_title);
        MenuItem item = nav.getMenu().findItem(itemId);
        if (itemId == R.id.nav_home || item == null) {
            title.setText(R.string.app_name);
        } else {
            title.setText(item.getTitle());
        }
    }

    /** Pads the gold top bar below the status bar, so the gold also fills the status bar area. */
    private void padTopForStatusBar(View content) {
        ViewCompat.setOnApplyWindowInsetsListener(content, new OnApplyWindowInsetsListener() {
            @Override
            public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat windowInsets) {
                Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                        | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(bars.left, bars.top, bars.right, 0);
                return windowInsets;
            }
        });
    }
}
