package com.zybooks.weight_tracker;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Adapter responsible for binding weight history data to the RecyclerView.
 * <p>
 * This adapter delegates edit and delete actions back to the hosting activity
 * to keep UI rendering separate from navigation and database logic.
 */
final class WeightAdapter extends RecyclerView.Adapter<WeightAdapter.WeightViewHolder> {

    /**
     * Callback for edit actions initiated from a list item.
     */
    @FunctionalInterface
    public interface OnEditClickListener { void onEdit(WeightEntry entry); }

    /**
     * Callback for delete actions initiated from a list item.
     */
    @FunctionalInterface
    public interface OnDeleteClickListener { void onDelete(WeightEntry entry); }

    private final List<WeightEntry> weightList;
    private final OnEditClickListener editListener;
    private final OnDeleteClickListener deleteListener;

    public WeightAdapter(
            List<WeightEntry> weightList,
            OnEditClickListener editListener,
            OnDeleteClickListener deleteListener
    ) {
        this.weightList = weightList;
        this.editListener = editListener;
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public WeightViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_weight_entry, parent, false);
        return new WeightViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WeightViewHolder holder, int position) {

        WeightEntry entry = weightList.get(position);
        Context context = holder.itemView.getContext();

        holder.dateText.setText(entry.getDate());

        holder.weightText.setText(context.getString(
                R.string.weight_display,
                entry.getWeight()
        ));

        String deltaDisplay = context.getString(R.string.stat_dash);

        if (position < weightList.size() - 1) {
            double current = entry.getWeight();
            double older = weightList.get(position + 1).getWeight();

            double delta = current - older;

            // Round to 1 decimal to match display precision
            double deltaRounded = Math.round(delta * 10.0) / 10.0;

            if (deltaRounded != 0.0) {
                deltaDisplay = context.getString(R.string.delta_value, deltaRounded); // e.g. +0.8 / -1.2
            }
        }

        holder.deltaText.setText(deltaDisplay);

        holder.editButton.setOnClickListener(v -> editListener.onEdit(entry));
        holder.deleteButton.setOnClickListener(v -> deleteListener.onDelete(entry));
    }

    @Override
    public int getItemCount() {
        return weightList.size();
    }

    /**
     * ViewHolder that caches references to item views for performance.
     * <p>
     * Declared static to avoid holding implicit references to the adapter or activity context.
     */
    public static class WeightViewHolder extends RecyclerView.ViewHolder {

        final TextView dateText;
        final TextView deltaText;
        final TextView weightText;
        final ImageButton editButton;
        final ImageButton deleteButton;

        WeightViewHolder(@NonNull View itemView) {
            super(itemView);
            dateText = itemView.findViewById(R.id.dateText);
            deltaText = itemView.findViewById(R.id.deltaText);
            weightText = itemView.findViewById(R.id.weightText);
            editButton = itemView.findViewById(R.id.editButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }
}