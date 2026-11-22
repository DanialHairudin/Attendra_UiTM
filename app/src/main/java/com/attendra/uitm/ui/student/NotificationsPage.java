package com.attendra.uitm.ui.student;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityViewCommand;
import androidx.fragment.app.Fragment;

import com.attendra.uitm.R;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.Db;
import com.attendra.uitm.data.HiddenSessions;
import com.attendra.uitm.data.Subscriptions;
import com.attendra.uitm.databinding.NotificationsPageBinding;
import com.attendra.uitm.databinding.ItemNotificationRowBinding;
import com.attendra.uitm.model.Session;
import com.attendra.uitm.ui.common.SwipeToDismiss;
import com.attendra.uitm.ui.common.Ui;
import com.attendra.uitm.util.Formats;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Student Notifications tab. */
public class NotificationsPage extends Fragment {

    /** How many recent notifications to show. */
    private static final int LIMIT = 50;

    /** One row of notifications/{uid}. */
    private static final class Item {
        String id;
        String title;
        String body;
        String sessionId;
        /** "alert" = attendance below 80%; null = a session. */
        String type;
        long time;
        boolean read;
    }

    private NotificationsPageBinding binding;
    private final Subscriptions subs = new Subscriptions();
    private List<Item> items; // Null = not loaded yet
    /** hiddenSessions/{uid}: notifications about these sessions are not listed. */
    private Set<String> hidden = new HashSet<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = NotificationsPageBinding.inflate(inflater, container, false);
        Ui.setEmpty(binding.emptyNotifications, R.drawable.ic_bell,
                R.string.empty_notifications_title, R.string.empty_notifications_body);
        binding.btnMarkAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                markAllRead();
            }
        });
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        watchNotifications(uid);
        watchHidden(uid);
    }

    /** Follows the student's notifications. */
    private void watchNotifications(String uid) {
        subs.listen(Db.root().child("notifications").child(uid), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                List<Item> list = new ArrayList<>();
                for (DataSnapshot n : snapshot.getChildren()) {
                    Item item = new Item();
                    item.id = n.getKey();
                    item.title = n.child("title").getValue(String.class);
                    item.body = n.child("body").getValue(String.class);
                    item.sessionId = n.child("sessionId").getValue(String.class);
                    item.type = n.child("type").getValue(String.class);
                    Long time = n.child("time").getValue(Long.class);
                    if (time == null) {
                        item.time = 0;
                    } else {
                        item.time = time;
                    }
                    item.read = Boolean.TRUE.equals(n.child("read").getValue(Boolean.class));
                    // Weekly sessions: the notification is shown only when its week starts.
                    Long showAt = n.child("showAt").getValue(Long.class);
                    if (showAt != null) {
                        if (showAt > System.currentTimeMillis()) continue;
                        item.time = showAt;
                    }
                    list.add(item);
                }
                // Newest first, and only the most recent LIMIT.
                Collections.sort(list, new Comparator<Item>() {
                    @Override
                    public int compare(Item a, Item b) {
                        return Long.compare(b.time, a.time);
                    }
                });
                if (list.size() > LIMIT) {
                    items = new ArrayList<>(list.subList(0, LIMIT));
                } else {
                    items = list;
                }
                render();
            }

            @Override
            public void onError(String message) {
                items = new ArrayList<>();
                render();
                showError(message);
            }
        });
    }

    /** Follows the sessions the student has hidden. */
    private void watchHidden(String uid) {
        subs.listen(HiddenSessions.ref(uid), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                hidden = HiddenSessions.ids(snapshot);
                render();
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });
    }

    /** The loaded notifications minus those about hidden sessions. */
    private List<Item> visible() {
        List<Item> list = new ArrayList<>();
        if (items != null) {
            for (Item n : items) {
                if (isAlert(n) || !hidden.contains(n.sessionId)) {
                    list.add(n);
                }
            }
        }
        return list;
    }

    @Override
    public void onStop() {
        super.onStop();
        subs.clear();
        items = null;
    }

    /** Draws the list again. */
    private void render() {
        if (binding == null) return;
        binding.listNotifications.removeAllViews();
        List<Item> shown = visible();
        boolean anyUnread = false;
        for (Item n : shown) {
            if (!n.read) {
                anyUnread = true;
            }
        }
        if (anyUnread) {
            binding.btnMarkAll.setVisibility(View.VISIBLE);
        } else {
            binding.btnMarkAll.setVisibility(View.GONE);
        }
        if (items == null || shown.isEmpty()) {
            if (items == null) {
                Ui.showListState(Ui.ListState.LOADING, binding.txtNotificationsLoading,
                        binding.emptyNotifications, binding.listNotifications);
            } else {
                Ui.showListState(Ui.ListState.EMPTY, binding.txtNotificationsLoading,
                        binding.emptyNotifications, binding.listNotifications);
            }
            return;
        }
        Ui.showListState(Ui.ListState.CONTENT, binding.txtNotificationsLoading,
                binding.emptyNotifications, binding.listNotifications);
        long now = System.currentTimeMillis();
        for (Item n : shown) {
            addRow(n, now);
        }
    }

    /** True for an "Attendance below 80%" notification. */
    private static boolean isAlert(Item n) {
        return "alert".equals(n.type);
    }

    /** Adds the row of one notification. */
    private void addRow(Item n, long now) {
        ItemNotificationRowBinding row = ItemNotificationRowBinding.inflate(
                getLayoutInflater(), binding.listNotifications, false);
        row.notifTitle.setText(n.title);
        row.notifBody.setText(n.body);
        row.notifTime.setText(when(n.time, now));
        if (isAlert(n)) {
            row.notifIcon.setImageResource(R.drawable.ic_warning);
        }
        if (n.read) {
            row.notifDot.setVisibility(View.INVISIBLE);
        } else {
            row.notifDot.setVisibility(View.VISIBLE);
        }
        // TalkBack reads "Unread" before the title when the dot is shown.
        if (n.read) {
            row.getRoot().setContentDescription(null);
        } else {
            row.getRoot().setContentDescription(getString(R.string.notifications_unread,
                    n.title));
        }
        row.getRoot().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(n);
            }
        });
        // Swipe to delete, long-press for Hide session / Delete.
        SwipeToDismiss.attach(row.getRoot(), new Runnable() {
            @Override
            public void run() {
                delete(n);
            }
        });
        row.getRoot().setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showMenu(n);
                return true;
            }
        });
        ViewCompat.addAccessibilityAction(row.getRoot(),
                getString(R.string.delete_notification), new AccessibilityViewCommand() {
                    @Override
                    public boolean perform(View view, AccessibilityViewCommand.CommandArguments args) {
                        delete(n);
                        return true;
                    }
                });
        Ui.addRow(binding.listNotifications, row.getRoot());
    }

    // ------------------------------------------------- hide and delete

    /** Long-press menu: "Hide session" (closed sessions only) and "Delete notification". */
    private void showMenu(Item n) {
        if (isAlert(n)) {
            showAlertMenu(n);
            return;
        }
        String[] options = {getString(R.string.hide_session), getString(R.string.delete_notification)};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(n.title)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            hideSession(n);
                        } else {
                            delete(n);
                        }
                    }
                })
                .show();
    }

    /** Long-press menu of an alert: it has no session to hide. */
    private void showAlertMenu(Item n) {
        String[] options = {getString(R.string.delete_notification)};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(n.title)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        delete(n);
                    }
                })
                .show();
    }

    /** Hides the notification's session from the student's lists. */
    private void hideSession(Item n) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || n.sessionId == null) return;
        Db.readOnce(Db.root().child("sessions").child(n.sessionId).child("status"),
                new Callback<DataSnapshot>() {
                    @Override
                    public void onSuccess(DataSnapshot status) {
                        if (!Session.STATUS_CLOSED.equals(status.getValue(String.class))) {
                            showError(getString(R.string.hide_open_refused));
                            return;
                        }
                        HiddenSessions.hide(uid, n.sessionId, new Callback<Void>() {
                            @Override
                            public void onSuccess(Void unused) {
                                showMessage(R.string.session_hidden);
                            }

                            @Override
                            public void onError(String message) {
                                showError(message);
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {
                        showError(message);
                    }
                });
    }

    /** Deletes this notification only (the student's own). */
    private void delete(Item n) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        Map<String, Object> updates = new HashMap<>();
        updates.put("notifications/" + uid + "/" + n.id, null);
        Db.write(updates, new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                showMessage(R.string.notification_deleted);
            }

            @Override
            public void onError(String message) {
                showError(message);
                render(); // Put the swiped row back
            }
        });
    }

    /** Shows a short message. */
    private void showMessage(int message) {
        if (binding != null) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
        }
    }

    /** "2:00 PM" today, "Yesterday", otherwise "12 Oct". */
    private String when(long time, long now) {
        if (time == 0) return "";
        if (Formats.sameDay(time, now)) return Formats.time(time);
        if (Formats.sameDay(time, now - 24L * 60 * 60 * 1000)) {
            return getString(R.string.notifications_yesterday);
        }
        return Formats.shortDate(time);
    }

    /** Marks it as read and opens its session. */
    private void open(Item n) {
        if (!n.read) {
            setRead(Collections.singletonList(n));
        }
        if (isAlert(n)) {
            ((StudentMainPage) requireActivity()).selectTab(R.id.nav_attendance);
        } else if (n.sessionId != null) {
            CheckInPage.open(requireContext(), n.sessionId);
        }
    }

    /** Marks every notification as read. */
    private void markAllRead() {
        if (items == null) return;
        List<Item> unread = new ArrayList<>();
        for (Item n : visible()) {
            if (!n.read) {
                unread.add(n);
            }
        }
        if (!unread.isEmpty()) {
            setRead(unread);
        }
    }

    /** One updateChildren that sets read = true on each of them. */
    private void setRead(List<Item> list) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        Map<String, Object> updates = new HashMap<>();
        for (Item n : list) {
            updates.put("notifications/" + uid + "/" + n.id + "/read", true);
        }
        Db.write(updates, new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                // The listener redraws the list.
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });
    }

    /** Shows an error message. */
    private void showError(String message) {
        if (binding != null) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
