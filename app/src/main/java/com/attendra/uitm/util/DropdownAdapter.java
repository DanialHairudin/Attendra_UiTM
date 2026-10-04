package com.attendra.uitm.util;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import androidx.annotation.NonNull;

import com.attendra.uitm.R;

import java.util.List;

/**
 * Adapter for the exposed dropdown menus (Programme, Part, Group).
 *
 * A normal ArrayAdapter filters its items by the text in the field. After a
 * choice is made, or after the screen rotates, the list then shows only the
 * chosen item. This adapter's filter does nothing, so the full list always shows.
 */
public class DropdownAdapter extends ArrayAdapter<String> {

    private final List<String> items;

    public DropdownAdapter(@NonNull Context context, @NonNull List<String> items) {
        super(context, R.layout.item_dropdown, items);
        this.items = items;
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                results.values = items;
                results.count = items.size();
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                notifyDataSetChanged();
            }
        };
    }
}
