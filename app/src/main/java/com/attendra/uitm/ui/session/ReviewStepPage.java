package com.attendra.uitm.ui.session;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.attendra.uitm.R;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.SessionJava;
import com.attendra.uitm.databinding.ReviewStepPageBinding;
import com.attendra.uitm.databinding.ItemInfoRowBinding;
import com.attendra.uitm.model.Course;
import com.attendra.uitm.model.Session;
import com.attendra.uitm.ui.common.SessionText;
import com.attendra.uitm.ui.common.Ui;
import com.attendra.uitm.ui.lecturer.LiveMonitorPage;
import com.attendra.uitm.data.SeedCache;
import com.attendra.uitm.util.Formats;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

/** Session Review Page: everything in one place, then "Start session". */
public class ReviewStepPage extends BaseStepPage {

    private ReviewStepPageBinding binding;
    private boolean starting;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = ReviewStepPageBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        CreateSessionViewModel vm = vm();
        Session s = vm.toSession();

        binding.txtTypeBadge.setText(SessionTypes.name(s.type));
        binding.txtTitle.setText(s.title);
        binding.txtSubtitle.setText(subtitle(s));

        String students;
        if (s.isClub()) {
            students = getResources().getQuantityString(R.plurals.members_count, vm.eligibleCount,
                    vm.eligibleCount);
        } else {
            students = getResources().getQuantityString(R.plurals.students_count, vm.eligibleCount,
                    vm.eligibleCount);
        }
        if (s.isClub()) {
            addRow(R.string.review_who, SessionText.join(SessionText.course(s), students));
        } else {
            addRow(R.string.review_who, SessionText.join(SessionText.groups(s), students));
        }
        addRow(R.string.review_where, SessionText.room(requireContext(), s));
        if ("pin".equals(s.where)) {
            addRow(R.string.review_geofence, getString(R.string.review_geofence_pin, s.radius));
        } else {
            addRow(R.string.review_geofence, getString(R.string.review_geofence_building, s.building,
                    s.radius));
        }
        addRow(R.string.review_time, SessionText.join(Formats.shortDate(s.start),
                Formats.timeRange(s.start, s.end)));
        addRow(R.string.review_late, getResources().getQuantityString(R.plurals.review_late_value,
                vm.lateMinutes, vm.lateMinutes, Formats.time(s.lateAfter)));

        if (vm.repeatWeekly) {
            List<Integer> weeks = vm.tickedWeeks();
            addRow(R.string.review_repeat, getString(R.string.review_repeat_value, weeks.size(),
                    Formats.shortDate(vm.startInWeek(weeks.get(0))),
                    Formats.shortDate(vm.startInWeek(weeks.get(weeks.size() - 1)))));
        }

        binding.txtNotify.setText(getResources().getQuantityString(R.plurals.review_notify,
                vm.eligibleCount, vm.eligibleCount));
    }

    /** "ICT602 · Mobile Technology and Development", or the club's full name. */
    private String subtitle(Session s) {
        if (s.isClub()) {
            com.attendra.uitm.model.Club c = SeedCache.club(s.clubId);
            if (c != null) {
                return c.name;
            }
            return s.clubId;
        }
        Course c = SeedCache.course(s.courseId);
        if (c != null) {
            return c.label();
        }
        return s.courseId;
    }

    /** Adds one row to the summary. */
    private void addRow(@StringRes int label, String value) {
        ItemInfoRowBinding row = ItemInfoRowBinding.inflate(getLayoutInflater(),
                binding.listSummary, false);
        row.infoLabel.setText(label);
        row.infoValue.setText(value);
        row.infoValue.setGravity(android.view.Gravity.START);
        Ui.addRow(binding.listSummary, row.getRoot());
    }

    @Override
    public int stepNumber() {
        return 5;
    }

    @Override
    public int continueLabel() {
        return R.string.start_session;
    }

    @Override
    protected boolean canContinue() {
        return !starting;
    }

    @Override
    public void onContinue() {
        if (starting) return;
        CreateSessionViewModel vm = vm();
        // The page may have stayed open past the closing time.
        if (vm.endTime() <= System.currentTimeMillis()) {
            Snackbar.make(binding.getRoot(), R.string.when_error_past, Snackbar.LENGTH_LONG).show();
            return;
        }
        starting = true;
        host().setContinueEnabled(false);
        host().setContinueText(R.string.starting_session);

        Session s = vm.toSession();
        String title = getString(R.string.notify_title, s.title);
        String body = SessionText.join(SessionText.course(s),
                SessionText.room(requireContext(), s),
                getString(R.string.notify_body_time, Formats.timeRange(s.start, s.end)));
        Callback<String> answer = new Callback<String>() {
            @Override
            public void onSuccess(String sessionId) {
                if (getActivity() == null) return;
                if (vm.repeatWeekly) {
                    int count = vm.tickedWeeks().size();
                    Toast.makeText(requireContext(), getString(R.string.weekly_created, count),
                            Toast.LENGTH_LONG).show();
                }
                // Weekly sessions that start later: nothing to monitor yet.
                if (!vm.repeatWeekly || startsNow(vm)) {
                    LiveMonitorPage.open(requireActivity(), sessionId);
                }
                requireActivity().finish();
            }

            @Override
            public void onError(String message) {
                starting = false;
                if (binding == null) return;
                host().setContinueText(R.string.start_session);
                host().setContinueEnabled(true);
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        };
        if (vm.repeatWeekly) {
            new SessionJava().createSeries(s, vm.tickedWeeks(), title, body, answer);
        } else {
            new SessionJava().create(s, title, body, answer);
        }
    }

    /** True if the first weekly session is open already. */
    private boolean startsNow(CreateSessionViewModel vm) {
        return vm.startInWeek(vm.tickedWeeks().get(0)) <= System.currentTimeMillis();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
