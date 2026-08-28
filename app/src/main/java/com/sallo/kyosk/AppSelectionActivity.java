package com.sallo.kyosk;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AppSelectionActivity extends AppCompatActivity {

    private PreferenceManager preferenceManager;
    private InstalledAppsAdapter installedAppsAdapter;
    private TextView emptyView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_selection);

        preferenceManager = new PreferenceManager(this);
        installedAppsAdapter = new InstalledAppsAdapter();
        emptyView = findViewById(R.id.installedAppsEmptyState);

        RecyclerView recyclerView = findViewById(R.id.installedAppsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(installedAppsAdapter);

        findViewById(R.id.saveAllowedAppsButton).setOnClickListener(v -> saveSelectedApps());
        loadInstalledApps();
    }

    private void loadInstalledApps() {
        PackageManager packageManager = getPackageManager();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(launcherIntent, 0);
        Set<String> selectedApps = preferenceManager.getAllowedApps();
        Map<String, AppModel> appMap = new LinkedHashMap<>();

        for (ResolveInfo resolveInfo : resolveInfos) {
            if (resolveInfo.activityInfo == null) {
                continue;
            }

            String packageName = resolveInfo.activityInfo.packageName;
            if (getPackageName().equals(packageName)) {
                continue;
            }

            if (!appMap.containsKey(packageName)) {
                AppModel appModel = new AppModel(
                        resolveInfo.loadLabel(packageManager).toString(),
                        packageName,
                        resolveInfo.loadIcon(packageManager)
                );
                appModel.setAllowed(selectedApps.contains(packageName));
                appMap.put(packageName, appModel);
            }
        }

        List<AppModel> items = new ArrayList<>(appMap.values());
        Collator collator = Collator.getInstance(Locale.getDefault());
        items.sort((left, right) -> collator.compare(left.getAppName(), right.getAppName()));

        installedAppsAdapter.submitList(items);
        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void saveSelectedApps() {
        Set<String> allowedPackages = new LinkedHashSet<>();
        for (AppModel appModel : installedAppsAdapter.getItems()) {
            if (appModel.isAllowed()) {
                allowedPackages.add(appModel.getPackageName());
            }
        }
        preferenceManager.setAllowedApps(allowedPackages);
        Toast.makeText(this, R.string.allowed_apps_saved, Toast.LENGTH_SHORT).show();
        finish();
    }
}
