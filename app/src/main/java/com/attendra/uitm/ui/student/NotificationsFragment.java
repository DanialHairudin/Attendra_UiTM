package com.attendra.uitm.ui.student;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.attendra.uitm.R;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.Db;
import com.attendra.uitm.data.Subscriptions;
import com.attendra.uitm.databinding.FragmentNotificationsBinding;
import com.attendra.uitm.databinding.ItemNotificationRowBinding;
import com.attendra.uitm.ui.common.Ui;
import com.attendra.uitm.util.Formats;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Student Notifications tab (CLAUDE.md 7 and 9, C8). Lists notifications/{uid},
 * which a lecturer writes for every eligible student when a session opens.
 * In-app only: there is no push (FCM) on the Spark plan.
 *
 * Tapping one marks it read and opens Check in for its session; the Check in
 * page itself shows "Session closed" when it is too late. "Mark all read"
 * marks every unread one in a single update. The rules let the owner change
 * only the `read` field.
 */
public class NotificationsFragment extends Fragment {

    /** How many recent notifications to show. */
    private static final int LIMIT = 50;

    /** One row of notifications/{uid}. */
    private static final class Item {
        String id;
        String title;
        String body;
        String sessionId;
        long time;
        boolean read;
    }

    private FragmentNotificationsBinding binding;
    private final Subscriptions subs = new Subscriptions();
    private List<Item> items; // null = not loaded yet

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentNotificationsBinding.inflate(inflater, container, false);
        Ui.setEmpty(binding.emptyNotifications, R.drawable.ic_bell,
                R.string.empty_notifications_title, R.string.empty_notifications_body);
        binding.btnMarkAll.setOnClickListener(v -> markAllRead());
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
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
                    Long time = n.child("time").getValue(Long.class);
                    item.time = time == null ? 0 : time;
                    item.read = Boolean.TRUE.equals(n.child("read").getValue(Boolean.class));
                    list.add(item);
                }
                // Newest first, and only the most recent LIMIT.
                Collections.sort(list, (a, b) -> Long.compare(b.time, a.time));
                items = list.size() > LIMIT ? new ArrayList<>(list.subList(0, LIMIT)) : list;
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

    @Override
    public void onStop() {
        super.onStop();
        subs.clear();
        items = null;
    }

    private void render() {
        if (binding == null) return;
        binding.listNotifications.removeAllViews();
        boolean anyUnread = false;
        if (items != null) {
            for (Item n : items) anyUnread |= !n.read;
        }
        binding.btnMarkAll.setVisibility(anyUnread ? View.VISIBLE : View.GONE);
        if (items == null || items.isEmpty()) {
            Ui.showListState(items == null ? Ui.ListState.LOADING : Ui.ListState.EMPTY,
                    binding.txtNotificationsLoading, binding.emptyNotifications,
                    binding.listNotifications);
            return;
        }
        Ui.showListState(Ui.ListState.CONTENT, binding.txtNotificationsLoading,
                binding.emptyNotifications, binding.listNotifications);
        long now = System.currentTimeMillis();
        for (Item n : items) {
            ItemNotificationRowBinding row = ItemNotificationRowBinding.inflate(
                    getLayoutInflater(), binding.listNotifications, false);
            row.notifTitle.setText(n.title);
            row.notifBody.setText(n.body);
            row.notifTime.setText(when(n.time, now));
            row.notifDot.setVisibility(n.read ? View.INVISIBLE : View.VISIBLE);
            // TalkBack reads "Unread" before the title when the dot is shown.
            row.getRoot().setContentDescription(n.read ? null
                    : getString(R.string.notifications_unread, n.title));
            row.getRoot().setOnClickListener(v -> open(n));
            Ui.addRow(binding.listNotifications, row.getRoot());
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

    private void open(Item n) {
        if (!n.read) setRead(Collections.singletonList(n));
        if (n.sessionId != null) CheckInActivity.open(requireContext(), n.sessionId);
    }

    private void markAllRead() {
        if (items == null) return;
        List<Item> unread = new ArrayList<>();
        for (Item n : items) if (!n.read) unread.add(n);
        if (!unread.isEmpty()) setRead(unread);
    }

    /** One updateChildren that sets read = true on each of them. */
    private void setRead(List<Item> list) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        Map<String, Object> updates = new HashMap<>();
        for (Item n : list) updates.put("notifications/" + uid + "/" + n.id + "/read", true);
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

    private void showError(String message) {
        if (binding != null) Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
