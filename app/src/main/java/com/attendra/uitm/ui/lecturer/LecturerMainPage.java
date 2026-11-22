package com.attendra.uitm.ui.lecturer;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.attendra.uitm.R;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.Db;
import com.attendra.uitm.data.TeachingJava;
import com.attendra.uitm.data.UserJava;
import com.attendra.uitm.databinding.LecturerMainPageBinding;
import com.attendra.uitm.model.Registration;
import com.attendra.uitm.ui.session.CreateSessionPage;
import com.attendra.uitm.ui.common.MainTabsPage;
import com.attendra.uitm.ui.settings.SettingsPage;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;

/** Lecturer area. */
public class LecturerMainPage extends MainTabsPage {

    private static final String EXTRA_CLASSES_TAB = "classes_tab";

    /** Brings the lecturer area back to the front on the Classes tab (from Profile). */
    public static void openClassesTab(Context from) {
        from.startActivity(new Intent(from, LecturerMainPage.class)
                .putExtra(EXTRA_CLASSES_TAB, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    /** True while the Create session guard is reading, so a double tap opens one wizard. */
    private boolean checkingCreate;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!requireSignedIn()) return;
        LecturerMainPageBinding binding = LecturerMainPageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setUpTabs(binding.mainContainer, binding.bottomNav, savedInstanceState);
        binding.btnFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCreateSession();
            }
        });
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra(EXTRA_CLASSES_TAB, false)) {
            selectTab(R.id.nav_classes);
        }
    }

    @Override
    public String role() {
        return Registration.ROLE_LECTURER;
    }

    @Override
    protected Fragment createTab(int itemId) {
        if (itemId == R.id.nav_classes) return new ClassesPage();
        if (itemId == R.id.nav_history) return new HistoryPage();
        if (itemId == R.id.nav_settings) return SettingsPage.newInstance(role());
        return new LecturerHomePage();
    }

    /** Opens the Create Session wizard. */
    public void openCreateSession() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || checkingCreate) return;
        checkingCreate = true;
        TeachingJava.hasCurrent(uid, new Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean hasClasses) {
                if (hasClasses) {
                    startWizard();
                } else {
                    checkClubs(uid);
                }
            }

            @Override
            public void onError(String message) {
                startWizard();
            }
        });
    }

    /** Opens Create Session if the lecturer has a class or a club. */
    private void checkClubs(String uid) {
        String role = Registration.ROLE_LECTURER;
        Db.readOnce(UserJava.clubsQuery(uid, role), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                if (!UserJava.clubIds(snapshot, uid, role).isEmpty()) {
                    startWizard();
                    return;
                }
                checkingCreate = false;
                if (isFinishing()) return;
                new MaterialAlertDialogBuilder(LecturerMainPage.this)
                        .setTitle(R.string.teach_needed)
                        .setMessage(R.string.teach_needed_body)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.teach_add_button, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                AddTeachingClassPage.open(LecturerMainPage.this);
                            }
                        })
                        .show();
            }

            @Override
            public void onError(String message) {
                startWizard();
            }
        });
    }

    /** Opens the Create Session pages. */
    private void startWizard() {
        checkingCreate = false;
        if (!isFinishing()) {
            CreateSessionPage.open(this);
        }
    }
}
