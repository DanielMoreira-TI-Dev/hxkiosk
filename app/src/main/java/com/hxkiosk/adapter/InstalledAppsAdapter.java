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
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

public class InstalledAppsAdapter extends RecyclerView.Adapter<InstalledAppsAdapter.InstalledAppViewHolder> {

    private final List<AppModel> appModels = new ArrayList<>();

    public void submitList(List<AppModel> items) {
        appModels.clear();
        appModels.addAll(items);
        notifyDataSetChanged();
    }

    public List<AppModel> getItems() {
        return new ArrayList<>(appModels);
    }

    @NonNull
    @Override
    public InstalledAppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_installed_app, parent, false);
        return new InstalledAppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull InstalledAppViewHolder holder, int position) {
        AppModel item = appModels.get(position);
        holder.iconView.setImageDrawable(item.getIcon());
        holder.nameView.setText(item.getAppName());
        holder.packageView.setText(item.getPackageName());
        holder.toggleView.setOnCheckedChangeListener(null);
        holder.toggleView.setChecked(item.isAllowed());
        holder.toggleView.setOnCheckedChangeListener((buttonView, isChecked) -> item.setAllowed(isChecked));
        holder.itemView.setOnClickListener(v -> holder.toggleView.setChecked(!holder.toggleView.isChecked()));
    }

    @Override
    public int getItemCount() {
        return appModels.size();
    }

    static class InstalledAppViewHolder extends RecyclerView.ViewHolder {
        private final ImageView iconView;
        private final TextView nameView;
        private final TextView packageView;
        private final MaterialSwitch toggleView;

        InstalledAppViewHolder(@NonNull View itemView) {
            super(itemView);
            iconView = itemView.findViewById(R.id.installedAppIcon);
            nameView = itemView.findViewById(R.id.installedAppName);
            packageView = itemView.findViewById(R.id.installedAppPackage);
            toggleView = itemView.findViewById(R.id.installedAppSwitch);
        }
    }
}
