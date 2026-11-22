package com.attendra.uitm.ui.session;

import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.GridLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.attendra.uitm.R;
import com.attendra.uitm.databinding.WhenStepPageBinding;
import com.attendra.uitm.util.Formats;
import com.google.android.material.chip.Chip;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.datepicker.MaterialPickerOnPositiveButtonClickListener;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Step 4, Session Time Page: date, opening time, closing time, and the "Mark as late after" chips
 * (5, 10, 15, 30 min).
 */
public class WhenStepPage extends BaseStepPage {

    private static final int[] LATE_CHOICES = {5, 10, 15, 30};

    private WhenStepPageBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = WhenStepPageBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.inputDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickDate();
            }
        });
        binding.inputStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickTime(true);
            }
        });
        binding.inputEnd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickTime(false);
            }
        });

        for (int minutes : LATE_CHOICES) {
            Chip chip = new Chip(requireContext()); // Widget.Attendra.Pill from the theme
            chip.setText(getString(R.string.minutes_short, minutes));
            chip.setCheckable(true);
            chip.setSaveEnabled(false); // The ViewModel keeps the choice
            chip.setChecked(vm().lateMinutes == minutes);
            chip.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton b, boolean checked) {
                    if (checked) {
                        vm().lateMinutes = minutes;
                        render();
                    }
                }
            });
            binding.chipsLate.addView(chip);
        }
        setUpRepeat();
        render();
    }

    // ---------------------------------------------------------------- repeat weekly

    /** The "Repeat weekly" tick and the "Until" spinner. */
    private void setUpRepeat() {
        // SECTION 1: the tick
        binding.checkRepeat.setSaveEnabled(false); // The ViewModel keeps the choice
        binding.checkRepeat.setChecked(vm().repeatWeekly);
        binding.checkRepeat.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                vm().repeatWeekly = checked;
                render();
            }
        });

        // SECTION 2: the spinner, Week 2 ... Week 16
        List<String> labels = new ArrayList<>();
        for (int week = 2; week <= CreateSessionViewModel.MAX_WEEKS; week++) {
            labels.add(getString(R.string.repeat_week, week));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerUntil.setSaveEnabled(false);
        binding.spinnerUntil.setAdapter(adapter);
        binding.spinnerUntil.setSelection(vm().untilWeek - 2);
        binding.spinnerUntil.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                vm().untilWeek = position + 2;
                render();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    /** One tick per week, and "13 sessions, every Tuesday, 2:00-2:20 PM". */
    private void renderRepeat() {
        CreateSessionViewModel vm = vm();
        if (!vm.repeatWeekly) {
            binding.groupRepeat.setVisibility(View.GONE);
            return;
        }
        binding.groupRepeat.setVisibility(View.VISIBLE);

        // SECTION 1: one tick per week. Untick a week to skip it.
        binding.gridWeeks.removeAllViews();
        for (int i = 1; i <= vm.untilWeek; i++) {
            final int week = i;
            CheckBox box = new CheckBox(requireContext());
            box.setText(getString(R.string.repeat_week_date, week,
                    Formats.dayOfMonth(vm.startInWeek(week)),
                    Formats.shortMonth(vm.startInWeek(week))));
            box.setSaveEnabled(false);
            box.setChecked(!vm.skippedWeeks.contains(week));
            box.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton b, boolean checked) {
                    if (checked) {
                        vm().skippedWeeks.remove(week);
                    } else {
                        vm().skippedWeeks.add(week);
                    }
                    renderSummary();
                }
            });
            GridLayout.LayoutParams half = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            half.width = 0;
            binding.gridWeeks.addView(box, half);
        }
        renderSummary();
    }

    /** "13 sessions, every Tuesday, 2:00-2:20 PM". */
    private void renderSummary() {
        CreateSessionViewModel vm = vm();
        int count = vm.tickedWeeks().size();
        String day = new SimpleDateFormat("EEEE", Locale.ENGLISH).format(new Date(vm.day));
        if (count < 2) {
            binding.txtRepeatSummary.setText(R.string.repeat_too_few);
        } else {
            binding.txtRepeatSummary.setText(getString(R.string.repeat_summary, count, day,
                    Formats.timeRange(vm.startTime(), vm.endTime())));
        }
        if (isAdded() && getView() != null) {
            refreshContinue();
        }
    }

    // ---------------------------------------------------------------- pickers

    /** Opens the date picker. */
    private void pickDate() {
        if (getParentFragmentManager().findFragmentByTag("date") != null) return;
        // MaterialDatePicker works in UTC: convert our local midnight to the same calendar date at
        // UTC midnight, and back again when a date is chosen.
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
        picker.addOnPositiveButtonClickListener(new MaterialPickerOnPositiveButtonClickListener<Long>() {
            @Override
            public void onPositiveButtonClick(Long selection) {
                Calendar picked = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                picked.setTimeInMillis(selection);
                Calendar day = Calendar.getInstance();
                day.clear();
                day.set(picked.get(Calendar.YEAR), picked.get(Calendar.MONTH),
                        picked.get(Calendar.DAY_OF_MONTH));
                vm().day = day.getTimeInMillis();
                render();
            }
        });
        picker.show(getParentFragmentManager(), "date");
    }

    /** Opens the time picker for the start or end time. */
    private void pickTime(boolean start) {
        if (getParentFragmentManager().findFragmentByTag("time") != null) return;
        int minute = vm().endMinute;
        int titleText = R.string.when_closes;
        if (start) {
            minute = vm().startMinute;
            titleText = R.string.when_opens;
        }
        int timeFormat = TimeFormat.CLOCK_12H;
        if (DateFormat.is24HourFormat(requireContext())) {
            timeFormat = TimeFormat.CLOCK_24H;
        }
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(timeFormat)
                .setHour(minute / 60)
                .setMinute(minute % 60)
                .setTitleText(titleText)
                .build();
        picker.addOnPositiveButtonClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int picked = picker.getHour() * 60 + picker.getMinute();
                if (start) {
                    // Keep the same length when the opening time moves.
                    int length = vm().endMinute - vm().startMinute;
                    vm().startMinute = picked;
                    if (length > 0) {
                        vm().endMinute = Math.min(picked + length, 24 * 60 - 1);
                    }
                } else {
                    vm().endMinute = picked;
                }
                render();
            }
        });
        picker.show(getParentFragmentManager(), "time");
    }

    // ------------------------------------------------------------------ render

    /** Draws the page again. */
    private void render() {
        if (binding == null) return;
        CreateSessionViewModel vm = vm();
        binding.inputDate.setText(new SimpleDateFormat("EEE, d MMM yyyy", Locale.ENGLISH)
                .format(new Date(vm.day)));
        binding.inputStart.setText(Formats.time(vm.startTime()));
        binding.inputEnd.setText(Formats.time(vm.endTime()));

        int error = timeError();
        if (error == 0) {
            binding.txtTimeError.setVisibility(View.GONE);
        } else {
            binding.txtTimeError.setVisibility(View.VISIBLE);
        }
        if (error != 0) {
            binding.txtTimeError.setText(error);
        }

        renderRepeat();
        binding.txtTimeNote.setText(getString(R.string.when_note,
                Formats.time(vm.lateAfterTime()), Formats.time(vm.endTime())));
        if (isAdded() && getView() != null) {
            refreshContinue();
        }
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
        if (vm().repeatWeekly && vm().tickedWeeks().size() < 2) return false;
        return timeError() == 0;
    }

    @Override
    public void onContinue() {
        render(); // Time may have passed while the page was open
        if (canContinue()) {
            host().showStep(new ReviewStepPage());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
