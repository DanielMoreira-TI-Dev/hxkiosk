package com.sallo.kyosk;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainKioskActivity extends AppCompatActivity {

    private PreferenceManager preferenceManager;
    private TapDetector tapDetector;
    private AllowedAppsAdapter allowedAppsAdapter;
    private WebView webView;
    private View webContainer;
    private View appsContainer;
    private View appsHeaderBar;
    private View webErrorContainer;
    private TextView webErrorMessage;
    private RecyclerView allowedAppsRecyclerView;
    private TextView emptyAppsText;
    private TextView modeFooterText;
    private TextView appsClockText;
    private TextView appsBatteryText;
    private TextView webClockText;
    private TextView webBatteryText;
    private TextView appsWifiIndicator;
    private TextView webWifiIndicator;
    private View appsWifiIcon;
    private View webStatusContainer;
    private View webWifiIcon;
    private View statusBarTouchBlocker;
    private boolean adminAccessPending;
    private boolean allowTemporaryExit;
    private ConnectivityManager connectivityManager;
    private boolean batteryReceiverRegistered;
    private boolean networkCallbackRegistered;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final java.text.DateFormat timeFormatter = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault());
    private final Runnable clockTicker = new Runnable() {
        @Override
        public void run() {
            updateClockViews();
            long now = System.currentTimeMillis();
            long delay = 60000L - (now % 60000L);
            uiHandler.postDelayed(this, delay);
        }
    };
    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            updateBatteryViews(intent);
        }
    };
    private final ConnectivityManager.NetworkCallback networkCallback =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    runOnUiThread(MainKioskActivity.this::updateConnectivityViews);
                }

                @Override
                public void onLost(@NonNull Network network) {
                    runOnUiThread(MainKioskActivity.this::updateConnectivityViews);
                }

                @Override
                public void onCapabilitiesChanged(
                        @NonNull Network network,
                        @NonNull NetworkCapabilities networkCapabilities
                ) {
                    runOnUiThread(MainKioskActivity.this::updateConnectivityViews);
                }
            };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_kiosk);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        preferenceManager = new PreferenceManager(this);
        tapDetector = new TapDetector(5, 1200L);
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        webContainer = findViewById(R.id.webModeContainer);
        appsContainer = findViewById(R.id.appsModeContainer);
        appsHeaderBar = findViewById(R.id.appsHeaderBar);
        webErrorContainer = findViewById(R.id.webErrorContainer);
        webErrorMessage = findViewById(R.id.webErrorMessage);
        webView = findViewById(R.id.kioskWebView);
        allowedAppsRecyclerView = findViewById(R.id.allowedAppsRecyclerView);
        emptyAppsText = findViewById(R.id.allowedAppsEmptyState);
        modeFooterText = findViewById(R.id.modeFooterText);
        appsClockText = findViewById(R.id.appsClockText);
        appsBatteryText = findViewById(R.id.appsBatteryText);
        webClockText = findViewById(R.id.webClockText);
        webBatteryText = findViewById(R.id.webBatteryText);
        appsWifiIndicator = findViewById(R.id.appsWifiIndicator);
        webWifiIndicator = findViewById(R.id.webWifiIndicator);
        appsWifiIcon = findViewById(R.id.appsWifiIcon);
        webStatusContainer = findViewById(R.id.webStatusContainer);
        webWifiIcon = findViewById(R.id.webWifiIcon);
        statusBarTouchBlocker = findViewById(R.id.statusBarTouchBlocker);

        findViewById(R.id.webRetryButton).setOnClickListener(v -> loadWebMode());

        setupAllowedAppsList();
        setupWebView();
        setupBackHandling();
        setupStatusPanels();
        setupAdminShortcut();
        setupStatusBarTouchBlocker();
        renderCurrentMode();
        applyKioskPolicies();
    }

    private void setupAllowedAppsList() {
        allowedAppsAdapter = new AllowedAppsAdapter(this::launchAllowedApp);
        allowedAppsRecyclerView.setAdapter(allowedAppsAdapter);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setSupportZoom(false);
        webView.getSettings().setBuiltInZoomControls(false);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!isAllowedNavigation(request.getUrl())) {
                    showWebError(getString(R.string.web_blocked_url));
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                webErrorContainer.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onReceivedError(WebView view, @NonNull WebResourceRequest request,
                                        @NonNull WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    showWebError(getString(R.string.web_offline));
                }
            }
        });
    }

    private void renderCurrentMode() {
        applyImmersiveMode();
        adminAccessPending = false;
        ViewCompat.setBackgroundTintList(
                appsHeaderBar,
                ContextCompat.getColorStateList(this, resolvePrimaryColorRes(preferenceManager.getPrimaryColor()))
        );

        if (PreferenceManager.MODE_APPS.equals(preferenceManager.getKioskMode())) {
            showAppsMode();
        } else {
            showWebMode();
        }
    }

    private void showWebMode() {
        webContainer.setVisibility(View.VISIBLE);
        appsContainer.setVisibility(View.GONE);
        webStatusContainer.setVisibility(View.VISIBLE);
        updateStatusBarTouchBlocker();
        loadWebMode();
    }

    private void loadWebMode() {
        webErrorContainer.setVisibility(View.GONE);
        webView.loadUrl(normalizeUrl(preferenceManager.getAllowedUrl()));
    }

    private boolean isAllowedNavigation(@Nullable Uri targetUri) {
        if (targetUri == null) {
            return false;
        }

        String scheme = targetUri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return false;
        }

        Uri allowedUri = Uri.parse(normalizeUrl(preferenceManager.getAllowedUrl()));
        String allowedHost = allowedUri.getHost();
        String targetHost = targetUri.getHost();
        if (TextUtils.isEmpty(allowedHost) || TextUtils.isEmpty(targetHost)) {
            return TextUtils.equals(allowedUri.toString(), targetUri.toString());
        }

        String normalizedAllowedHost = allowedHost.toLowerCase(Locale.ROOT);
        String normalizedTargetHost = targetHost.toLowerCase(Locale.ROOT);
        return normalizedTargetHost.equals(normalizedAllowedHost)
                || normalizedTargetHost.endsWith("." + normalizedAllowedHost);
    }

    private void showAppsMode() {
        webContainer.setVisibility(View.GONE);
        appsContainer.setVisibility(View.VISIBLE);
        webStatusContainer.setVisibility(View.GONE);
        updateStatusBarTouchBlocker();

        int columns = Math.max(3, preferenceManager.getGridColumns());
        allowedAppsRecyclerView.setLayoutManager(new GridLayoutManager(this, columns));
        loadAllowedApps();
    }

    private void loadAllowedApps() {
        Set<String> allowedPackages = preferenceManager.getAllowedApps();
        List<AppModel> visibleApps = new ArrayList<>();
        PackageManager packageManager = getPackageManager();

        for (String packageName : allowedPackages) {
            try {
                Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
                if (launchIntent == null) {
                    continue;
                }
                ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageName, 0);
                CharSequence label = packageManager.getApplicationLabel(applicationInfo);
                AppModel appModel = new AppModel(
                        label == null ? packageName : label.toString(),
                        packageName,
                        packageManager.getApplicationIcon(packageName)
                );
                visibleApps.add(appModel);
            } catch (PackageManager.NameNotFoundException ignored) {
                // Ignore packages no longer installed on the device.
            }
        }

        Collections.sort(visibleApps, Comparator.comparing(
                app -> app.getAppName().toLowerCase(Locale.ROOT)
        ));
        allowedAppsAdapter.submitList(visibleApps);
        emptyAppsText.setVisibility(visibleApps.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void launchAllowedApp(AppModel appModel) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(appModel.getPackageName());
        if (launchIntent == null) {
            Toast.makeText(this, R.string.no_allowed_apps, Toast.LENGTH_SHORT).show();
            return;
        }
        allowTemporaryExit = true;
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launchIntent);
    }

    private void showWebError(String message) {
        webView.setVisibility(View.INVISIBLE);
        webErrorContainer.setVisibility(View.VISIBLE);
        webErrorMessage.setText(message);
    }

    private String normalizeUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return "https://example.com";
        }
        String trimmed = url.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return "https://" + trimmed;
        }
        return trimmed;
    }

    private void openAdminAccess(boolean openExitAfterAuth) {
        if (adminAccessPending) {
            return;
        }
        adminAccessPending = true;
        allowTemporaryExit = true;
        Intent intent = new Intent(this, AdminAccessActivity.class);
        intent.putExtra(AdminAccessActivity.EXTRA_OPEN_EXIT_AFTER_AUTH, openExitAfterAuth);
        startActivity(intent);
    }

    private void setupAdminShortcut() {
        modeFooterText.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                openAdminAccess(false);
                return true;
            }
            return false;
        });
        ViewCompat.setOnApplyWindowInsetsListener(modeFooterText, (view, windowInsets) -> {
            updateOverlayPositions(windowInsets);
            return windowInsets;
        });
        modeFooterText.bringToFront();
        ViewCompat.requestApplyInsets(modeFooterText);
    }

    private void setupBackHandling() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                boolean blockBack = preferenceManager.getBooleanConfig(
                        PreferenceManager.KEY_BLOCK_BACK_BUTTON,
                        false
                );
                boolean preventExit = preferenceManager.getBooleanConfig(
                        PreferenceManager.KEY_PREVENT_KIOSK_EXIT,
                        false
                );
                if (blockBack || preventExit) {
                    Toast.makeText(MainKioskActivity.this, R.string.kiosk_safe_mode, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (webContainer.getVisibility() == View.VISIBLE && webView.canGoBack()) {
                    webView.goBack();
                    return;
                }
                setEnabled(false);
                try {
                    getOnBackPressedDispatcher().onBackPressed();
                } finally {
                    setEnabled(true);
                }
            }
        });
    }

    private void setupStatusPanels() {
        ViewCompat.setOnApplyWindowInsetsListener(webStatusContainer, (view, windowInsets) -> {
            updateOverlayPositions(windowInsets);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(webStatusContainer);
    }

    private void updateOverlayPositions(@NonNull WindowInsetsCompat windowInsets) {
        Insets statusInsets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
        int topSpacing = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                18,
                getResources().getDisplayMetrics()
        );
        int blockerSpacing = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                8,
                getResources().getDisplayMetrics()
        );
        int topMargin = statusInsets.top + topSpacing;
        if (statusBarTouchBlocker.getVisibility() == View.VISIBLE) {
            topMargin = statusBarTouchBlocker.getLayoutParams().height + blockerSpacing;
        }
        FrameLayout.LayoutParams shortcutLayoutParams =
                (FrameLayout.LayoutParams) modeFooterText.getLayoutParams();
        shortcutLayoutParams.topMargin = topMargin;
        modeFooterText.setLayoutParams(shortcutLayoutParams);

        FrameLayout.LayoutParams webStatusLayoutParams =
                (FrameLayout.LayoutParams) webStatusContainer.getLayoutParams();
        webStatusLayoutParams.topMargin = topMargin;
        webStatusContainer.setLayoutParams(webStatusLayoutParams);
        modeFooterText.bringToFront();
        webStatusContainer.bringToFront();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN && tapDetector.registerTap()) {
            openAdminAccess(false);
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onResume() {
        super.onResume();
        allowTemporaryExit = false;
        renderCurrentMode();
        applyKioskPolicies();
        startClockTicker();
        registerBatteryReceiver();
        registerNetworkCallback();
        updateConnectivityViews();
    }

    @Override
    protected void onPause() {
        stopClockTicker();
        unregisterBatteryReceiver();
        unregisterNetworkCallback();
        super.onPause();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (preferenceManager.getBooleanConfig(PreferenceManager.KEY_PREVENT_KIOSK_EXIT, false)
                && !allowTemporaryExit) {
            // TODO: Real kiosk enforcement should migrate to Lock Task Mode with Device Owner.
            Intent intent = new Intent(this, MainKioskActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && event.getKeyCode() == KeyEvent.KEYCODE_APP_SWITCH
                && preferenceManager.getBooleanConfig(PreferenceManager.KEY_BLOCK_RECENT_BUTTON, false)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveMode();
        }
    }

    private void applyImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }

    private void setupStatusBarTouchBlocker() {
        statusBarTouchBlocker.setOnTouchListener((v, event) -> {
            if (preferenceManager.getBooleanConfig(PreferenceManager.KEY_BLOCK_NOTIFICATIONS, false)) {
                applyImmersiveMode();
                return true;
            }
            return false;
        });

        ViewCompat.setOnApplyWindowInsetsListener(statusBarTouchBlocker, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            int minTouchHeight = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    32,
                    getResources().getDisplayMetrics()
            );
            view.getLayoutParams().height = Math.max(insets.top + minTouchHeight, minTouchHeight * 3);
            view.requestLayout();
            updateOverlayPositions(windowInsets);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(statusBarTouchBlocker);
    }

    private void updateStatusBarTouchBlocker() {
        boolean shouldBlock = preferenceManager.getBooleanConfig(
                PreferenceManager.KEY_BLOCK_NOTIFICATIONS,
                false
        );
        statusBarTouchBlocker.setVisibility(shouldBlock ? View.VISIBLE : View.GONE);
        ViewCompat.requestApplyInsets(modeFooterText);
        ViewCompat.requestApplyInsets(webStatusContainer);
    }

    private void applyKioskPolicies() {
        KioskPolicyManager.applyDeviceOwnerPolicies(this, preferenceManager);
        KioskPolicyManager.applyLockTaskMode(this, preferenceManager);
        updateStatusBarTouchBlocker();
    }

    private void startClockTicker() {
        uiHandler.removeCallbacks(clockTicker);
        clockTicker.run();
    }

    private void stopClockTicker() {
        uiHandler.removeCallbacks(clockTicker);
    }

    private void updateClockViews() {
        String timeText = timeFormatter.format(new Date());
        appsClockText.setText(timeText);
        webClockText.setText(timeText);
    }

    private void registerBatteryReceiver() {
        if (batteryReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent stickyIntent = registerReceiver(batteryReceiver, filter);
        batteryReceiverRegistered = true;
        if (stickyIntent != null) {
            updateBatteryViews(stickyIntent);
        }
    }

    private void unregisterBatteryReceiver() {
        if (!batteryReceiverRegistered) {
            return;
        }
        unregisterReceiver(batteryReceiver);
        batteryReceiverRegistered = false;
    }

    private void updateBatteryViews(@NonNull Intent batteryIntent) {
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (level < 0 || scale <= 0) {
            return;
        }
        int percentage = Math.round((level * 100f) / scale);
        String batteryText = percentage + "%";
        appsBatteryText.setText(batteryText);
        webBatteryText.setText(batteryText);
    }

    private void registerNetworkCallback() {
        if (networkCallbackRegistered || connectivityManager == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
            networkCallbackRegistered = true;
        }
    }

    private void unregisterNetworkCallback() {
        if (!networkCallbackRegistered || connectivityManager == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
            networkCallbackRegistered = false;
        }
    }

    private void updateConnectivityViews() {
        boolean isOnline = isInternetAvailable();
        appsWifiIcon.setAlpha(isOnline ? 1f : 0.55f);
        appsWifiIndicator.setBackgroundResource(
                isOnline ? R.drawable.bg_status_online : R.drawable.bg_status_offline
        );
        appsWifiIndicator.setText(isOnline ? "" : "x");
        appsWifiIndicator.setTextColor(
                ContextCompat.getColor(this, android.R.color.white)
        );

        webWifiIcon.setAlpha(isOnline ? 1f : 0.55f);
        webWifiIndicator.setBackgroundResource(
                isOnline ? R.drawable.bg_status_online : R.drawable.bg_status_offline
        );
        webWifiIndicator.setText(isOnline ? "" : "x");
        webWifiIndicator.setTextColor(
                ContextCompat.getColor(this, android.R.color.white)
        );
    }

    private boolean isInternetAvailable() {
        if (connectivityManager == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = connectivityManager.getActiveNetwork();
            if (activeNetwork == null) {
                return false;
            }
            NetworkCapabilities capabilities =
                    connectivityManager.getNetworkCapabilities(activeNetwork);
            return capabilities != null
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }
        android.net.NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    private int resolvePrimaryColorRes(String colorKey) {
        if ("gray".equals(colorKey)) {
            return R.color.sallo_text_secondary;
        }
        if ("brown".equals(colorKey)) {
            return R.color.sallo_warning;
        }
        if ("dark_blue".equals(colorKey)) {
            return R.color.sallo_primary_dark;
        }
        return R.color.sallo_primary;
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
