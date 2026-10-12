package com.attendra.uitm.ui.session;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.attendra.uitm.R;
import com.attendra.uitm.data.Callback;
import com.attendra.uitm.data.SessionRepository;
import com.attendra.uitm.databinding.FragmentStepReviewBinding;
import com.attendra.uitm.databinding.ItemInfoRowBinding;
import com.attendra.uitm.model.Course;
import com.attendra.uitm.model.Session;
import com.attendra.uitm.ui.common.SessionText;
import com.attendra.uitm.ui.common.Ui;
import com.attendra.uitm.ui.lecturer.LiveMonitorActivity;
import com.attendra.uitm.data.SeedCache;
import com.attendra.uitm.util.Formats;
import com.google.android.material.snackbar.Snackbar;

/**
 * Session Review Page: everything in one place, then "Start session".
 * Starting writes the session, its indexes and one notification per eligible
 * student in ONE updateChildren (SessionRepository.create), then opens the
 * Live Monitor.
 */
public class ReviewStepFragment extends BaseStepFragment {

    private FragmentStepReviewBinding binding;
    private boolean starting;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentStepReviewBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        CreateSessionViewModel vm = vm();
        Session s = vm.toSession();

        binding.txtTypeBadge.setText(SessionTypes.name(s.type));
        binding.txtTitle.setText(s.title);
        binding.txtSubtitle.setText(subtitle(s));

        String students = getResources().getQuantityString(
                s.isClub() ? R.plurals.members_count : R.plurals.students_count,
                vm.eligibleCount, vm.eligibleCount);
        addRow(R.string.review_who, s.isClub() ? SessionText.join(SessionText.course(s), students)
                : SessionText.join(SessionText.groups(s), students));
        addRow(R.string.review_where, SessionText.room(requireContext(), s));
        addRow(R.string.review_geofence, "pin".equals(s.where)
                ? getString(R.string.review_geofence_pin, s.radius)
                : getString(R.string.review_geofence_building, s.building, s.radius));
        addRow(R.string.review_time, SessionText.join(Formats.shortDate(s.start),
                Formats.timeRange(s.start, s.end)));
        addRow(R.string.review_late, getResources().getQuantityString(R.plurals.review_late_value,
                vm.lateMinutes, vm.lateMinutes, Formats.time(s.lateAfter)));

        binding.txtNotify.setText(getResources().getQuantityString(R.plurals.review_notify,
                vm.eligibleCount, vm.eligibleCount));
    }

    /** "ICT602 · Mobile Technology and Development", or the club's full name. */
    private String subtitle(Session s) {
        if (s.isClub()) {
            com.attendra.uitm.model.Club c = SeedCache.club(s.clubId);
            return c != null ? c.name : s.clubId;
        }
        Course c = SeedCache.course(s.courseId);
        return c != null ? c.label() : s.courseId;
    }

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
        new SessionRepository().create(s, title, body, new Callback<String>() {
            @Override
            public void onSuccess(String sessionId) {
                if (getActivity() == null) return;
                LiveMonitorActivity.open(requireActivity(), sessionId);
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
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
