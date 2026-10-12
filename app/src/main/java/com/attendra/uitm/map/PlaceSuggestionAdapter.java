package com.attendra.uitm.map;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.attendra.uitm.R;

import java.util.ArrayList;
import java.util.List;

/** The list of place suggestions under the search box. */
public class PlaceSuggestionAdapter extends RecyclerView.Adapter<PlaceSuggestionAdapter.VH> {

    public interface OnPick { void onPick(PlaceResult place); }

    private final List<PlaceResult> items = new ArrayList<>();
    private final OnPick onPick;

    public PlaceSuggestionAdapter(OnPick onPick) { this.onPick = onPick; }

    public void submit(List<PlaceResult> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_place_suggestion, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        PlaceResult p = items.get(position);
        h.title.setText(p.title);
        h.subtitle.setText(p.subtitle);
        h.subtitle.setVisibility(p.subtitle.isEmpty() ? View.GONE : View.VISIBLE);
        h.itemView.setOnClickListener(v -> onPick.onPick(p));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title, subtitle;
        VH(View v) {
            super(v);
            title = v.findViewById(R.id.tvPlaceTitle);
            subtitle = v.findViewById(R.id.tvPlaceSubtitle);
        }
    }
}
