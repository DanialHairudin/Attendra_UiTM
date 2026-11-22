package com.attendra.uitm.util;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import androidx.annotation.NonNull;

import com.attendra.uitm.R;

import java.util.List;

/**
 * Adapter for the exposed dropdown menus (Programme, Part, Group). It shows a list of texts. The
 * page keeps its own list in the same order, so list.get(position) is the item that was tapped.
 */
public class DropdownAdapter extends ArrayAdapter<String> {

    private final List<String> labels;

    public DropdownAdapter(@NonNull Context context, @NonNull List<String> labels) {
        super(context, R.layout.item_dropdown, labels);
        this.labels = labels;
    }

    // Always lists every row, whatever text is already in the field.
    @NonNull
    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                results.values = labels;
                results.count = labels.size();
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                notifyDataSetChanged();
            }
        };
    }
}
