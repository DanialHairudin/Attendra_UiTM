package com.attendra.uitm.ui.session;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.attendra.uitm.R;
import com.attendra.uitm.databinding.TypeStepPageBinding;
import com.attendra.uitm.databinding.ItemTypeCardBinding;
import com.attendra.uitm.model.Session;
import com.attendra.uitm.ui.common.Ui;

import java.util.LinkedHashMap;
import java.util.Map;

/** Step 1, Session Type Page: Class, Test, SULAM or Club. */
public class TypeStepPage extends BaseStepPage {

    private TypeStepPageBinding binding;
    /** Session type → its card. */
    private final Map<String, ItemTypeCardBinding> cards = new LinkedHashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = TypeStepPageBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        bind(binding.cardClass, Session.TYPE_CLASS, "C", R.string.type_class, R.string.type_class_desc);
        bind(binding.cardTest, Session.TYPE_TEST, "T", R.string.type_test, R.string.type_test_desc);
        bind(binding.cardSulam, Session.TYPE_SULAM, "S", R.string.type_sulam, R.string.type_sulam_desc);
        bind(binding.cardClub, Session.TYPE_CLUB, "K", R.string.type_club, R.string.type_club_desc);
        showSelection();
    }

    /** Fills one session type card. */
    private void bind(ItemTypeCardBinding card, String type, String mark,
                      @StringRes int name, @StringRes int desc) {
        cards.put(type, card);
        card.typeMark.setText(mark);
        card.typeName.setText(name);
        card.typeDesc.setText(desc);
        card.getRoot().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                vm().setType(type);
                showSelection();
                refreshContinue();
            }
        });
    }

    /** The chosen card gets a 2dp gold border. */
    private void showSelection() {
        for (Map.Entry<String, ItemTypeCardBinding> e : cards.entrySet()) {
            boolean on = vm().isType(e.getKey());
            e.getValue().getRoot().setSelected(on); // Read by TalkBack as "selected"
            if (on) {
                e.getValue().getRoot().setStrokeColor(requireContext().getColor(R.color.gold));
                e.getValue().getRoot().setStrokeWidth(Ui.dp(requireContext(), 2));
            } else {
                e.getValue().getRoot().setStrokeColor(requireContext().getColor(R.color.line));
                e.getValue().getRoot().setStrokeWidth(Ui.dp(requireContext(), 1));
            }
        }
    }

    @Override
    public int stepNumber() {
        return 1;
    }

    @Override
    public int continueLabel() {
        return R.string.continue_label;
    }

    @Override
    protected boolean canContinue() {
        return vm().type != null;
    }

    @Override
    public void onContinue() {
        if (canContinue()) {
            host().showStep(new WhoStepPage());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        cards.clear();
    }
}
