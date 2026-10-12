package com.attendra.uitm.ui.session;

import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.attendra.uitm.R;
import com.attendra.uitm.databinding.FragmentStepWhenBinding;
import com.attendra.uitm.util.Formats;
import com.google.android.material.chip.Chip;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Step 4, Session Time Page: date, opening time, closing time, and the
 * "Mark as late after" chips (5, 10, 15, 30 min).
 * Rules: the closing time must be after the opening time and still in the
 * future, and the late mark must come before closing.
 */
public class WhenStepFragment extends BaseStepFragment {

    private static final int[] LATE_CHOICES = {5, 10, 15, 30};

    private FragmentStepWhenBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentStepWhenBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.inputDate.setOnClickListener(v -> pickDate());
        binding.inputStart.setOnClickListener(v -> pickTime(true));
        binding.inputEnd.setOnClickListener(v -> pickTime(false));

        for (int minutes : LATE_CHOICES) {
            Chip chip = new Chip(requireContext()); // Widget.Attendra.Pill from the theme
            chip.setText(getString(R.string.minutes_short, minutes));
            chip.setCheckable(true);
            chip.setSaveEnabled(false); // the ViewModel keeps the choice
            chip.setChecked(vm().lateMinutes == minutes);
            chip.setOnCheckedChangeListener((b, checked) -> {
                if (checked) {
                    vm().lateMinutes = minutes;
                    render();
                }
            });
            binding.chipsLate.addView(chip);
        }
        render();
    }

    // ---------------------------------------------------------------- pickers

    private void pickDate() {
        if (getParentFragmentManager().findFragmentByTag("date") != null) return;
        // MaterialDatePicker works in UTC: convert our local midnight to the same
        // calendar date at UTC midnight, and back again when a date is chosen.
        Calendar local = Calendar.getInstance();
        local.setTimeInMillis(vm().day);
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH),
                local.get(Calendar.DAY_OF_MONTH));
        long todayUtc = MaterialDatePicker.todayInUtcMilliseconds();

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.when_date)
                .setSelection(utc.getTimeInMillis())
                .setCalendarConstraints(new CalendarConstraints.Builder()
                        .setValidator(DateValidatorPointForward.from(todayUtc)).build())
                .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            Calendar picked = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            picked.setTimeInMillis(selection);
            Calendar day = Calendar.getInstance();
            day.clear();
            day.set(picked.get(Calendar.YEAR), picked.get(Calendar.MONTH),
                    picked.get(Calendar.DAY_OF_MONTH));
            vm().day = day.getTimeInMillis();
            render();
        });
        picker.show(getParentFragmentManager(), "date");
    }

    private void pickTime(boolean start) {
        if (getParentFragmentManager().findFragmentByTag("time") != null) return;
        int minute = start ? vm().startMinute : vm().endMinute;
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(DateFormat.is24HourFormat(requireContext())
                        ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(minute / 60)
                .setMinute(minute % 60)
                .setTitleText(start ? R.string.when_opens : R.string.when_closes)
                .build();
        picker.addOnPositiveButtonClickListener(v -> {
            int picked = picker.getHour() * 60 + picker.getMinute();
            if (start) {
                // Keep the same length when the opening time moves.
                int length = vm().endMinute - vm().startMinute;
                vm().startMinute = picked;
                if (length > 0) vm().endMinute = Math.min(picked + length, 24 * 60 - 1);
            } else {
                vm().endMinute = picked;
            }
            render();
        });
        picker.show(getParentFragmentManager(), "time");
    }

    // ------------------------------------------------------------------ render

    private void render() {
        if (binding == null) return;
        CreateSessionViewModel vm = vm();
        binding.inputDate.setText(new SimpleDateFormat("EEE, d MMM yyyy", Locale.ENGLISH)
                .format(new Date(vm.day)));
        binding.inputStart.setText(Formats.time(vm.startTime()));
        binding.inputEnd.setText(Formats.time(vm.endTime()));

        int error = timeError();
        binding.txtTimeError.setVisibility(error == 0 ? View.GONE : View.VISIBLE);
        if (error != 0) binding.txtTimeError.setText(error);

        binding.txtTimeNote.setText(getString(R.string.when_note,
                Formats.time(vm.lateAfterTime()), Formats.time(vm.endTime())));
        if (isAdded() && getView() != null) refreshContinue();
    }

    /** The first rule that is broken, or 0 if the times are fine. */
    @StringRes
    private int timeError() {
        CreateSessionViewModel vm = vm();
        if (vm.endMinute <= vm.startMinute) return R.string.when_error_order;
        if (vm.endTime() <= System.currentTimeMillis()) return R.string.when_error_past;
        if (vm.lateAfterTime() >= vm.endTime()) return R.string.when_error_late;
        return 0;
    }

    @Override
    public int stepNumber() {
        return 4;
    }

    @Override
    public int continueLabel() {
        return R.string.when_review;
    }

    @Override
    protected boolean canContinue() {
        return timeError() == 0;
    }

    @Override
    public void onContinue() {
        render(); // time may have passed while the page was open
        if (canContinue()) host().showStep(new ReviewStepFragment());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
