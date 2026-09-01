package com.hxkiosk.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hxkiosk.R;
import com.hxkiosk.model.AppModel;

import java.util.ArrayList;
import java.util.List;

public class AllowedAppsAdapter extends RecyclerView.Adapter<AllowedAppsAdapter.AllowedAppViewHolder> {

    public interface OnAppClickListener {
        void onAppClick(AppModel appModel);
    }

    private final List<AppModel> appModels = new ArrayList<>();
    private final OnAppClickListener onAppClickListener;

    public AllowedAppsAdapter(OnAppClickListener onAppClickListener) {
        this.onAppClickListener = onAppClickListener;
    }

    public void submitList(List<AppModel> items) {
        appModels.clear();
        appModels.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AllowedAppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_allowed_app, parent, false);
        return new AllowedAppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AllowedAppViewHolder holder, int position) {
        AppModel item = appModels.get(position);
        holder.iconView.setImageDrawable(item.getIcon());
        holder.nameView.setText(item.getAppName());
        holder.iconContainer.setOnClickListener(v -> onAppClickListener.onAppClick(item));
        holder.itemView.setOnClickListener(v -> onAppClickListener.onAppClick(item));
    }

    @Override
    public int getItemCount() {
        return appModels.size();
    }

    static class AllowedAppViewHolder extends RecyclerView.ViewHolder {
        private final View iconContainer;
        private final ImageView iconView;
        private final TextView nameView;

        AllowedAppViewHolder(@NonNull View itemView) {
            super(itemView);
            iconContainer = itemView.findViewById(R.id.allowedAppIconContainer);
            iconView = itemView.findViewById(R.id.allowedAppIcon);
            nameView = itemView.findViewById(R.id.allowedAppName);
        }
    }
}
