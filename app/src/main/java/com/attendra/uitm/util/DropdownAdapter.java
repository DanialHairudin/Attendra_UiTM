package com.attendra.uitm.util;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.attendra.uitm.R;

import java.util.List;

/** Adapter for the exposed dropdown menus (Programme, Part, Group). */
public class DropdownAdapter<T> extends ArrayAdapter<T> {

    /** Turns an item into the text shown in the list and in the field. */
    public interface Labeler<T> {
        String label(T item);
    }

    private final List<T> items;
    private final Labeler<T> labeler;

    public DropdownAdapter(@NonNull Context context, @NonNull List<T> items,
                           @NonNull Labeler<T> labeler) {
        super(context, R.layout.item_dropdown, items);
        this.items = items;
        this.labeler = labeler;
    }

    /** The text for one item, e.g. "CS259 · Bachelor of Information Systems (Hons.) …". */
    public String labelOf(T item) {
        return labeler.label(item);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        TextView row = (TextView) super.getView(position, convertView, parent);
        row.setText(labeler.label(items.get(position)));
        return row;
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

            // The field shows this text after a choice, so it uses the same label as the list rows.
            @SuppressWarnings("unchecked")
            @Override
            public CharSequence convertResultToString(Object resultValue) {
                return labeler.label((T) resultValue);
            }
        };
    }
}
