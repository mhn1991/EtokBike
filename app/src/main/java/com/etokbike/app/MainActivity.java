package com.etokbike.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String BUNDLED_MANIFEST_PATH = "mock/manifest.json";
    private static final String CUSTOMER_PREFS = "etokbike_customer";
    private static final String ACTIVE_MANIFEST_URL_PREF = "active_manifest_url";
    private static final String TELEMETRY_DEVICE_ID_PREF = "telemetry_device_id";
    private static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final long TELEMETRY_HEARTBEAT_MS = 60000L;
    private static final long INTRO_MIN_DURATION_MS = 700L;
    private static final long SCREEN_ANIMATION_MS = 220L;
    private static final long SCREEN_STAGGER_MS = 24L;
    private static final long PRESS_ANIMATION_MS = 90L;
    private static final int SCREEN_ANIMATION_CHILD_LIMIT = 8;
    private static final DecelerateInterpolator EASE_OUT = new DecelerateInterpolator(1.6f);
    private static final int CARD_MEDIA_WIDTH_DP = 268;
    private static final int CARD_MEDIA_HEIGHT_DP = 168;
    private static final int FEATURED_CARD_MEDIA_HEIGHT_DP = 188;
    private static final int DETAIL_MEDIA_HEIGHT_DP = 260;
    private static final int GALLERY_TILE_HEIGHT_DP = 150;
    private static final int THUMBNAIL_MEDIA_SIZE_DP = 88;

    private static final int RED = Color.rgb(215, 25, 32);
    private static final int DARK_RED = Color.rgb(176, 20, 26);
    private static final int ACCENT = RED;
    private static final int BLACK = Color.rgb(16, 17, 20);
    private static final int WHITE = Color.WHITE;
    private static final int BACKGROUND = Color.rgb(250, 248, 245);
    private static final int SURFACE = WHITE;
    private static final int SURFACE_ALT = Color.rgb(243, 241, 237);
    private static final int BORDER = Color.rgb(229, 226, 220);
    private static final int MUTED = Color.rgb(98, 99, 104);
    private static final int TOP_BAR_SURFACE = SURFACE;
    private static final int HERO_SURFACE = Color.rgb(255, 240, 241);
    private static final int HERO_PANEL = SURFACE;
    private static final int HERO_BORDER = Color.rgb(246, 198, 202);
    private static final int RED_TINT = HERO_SURFACE;
    private static final int SECONDARY = Color.rgb(27, 77, 62);
    private static final int SUCCESS = Color.rgb(15, 118, 110);
    private static final int SUCCESS_SOFT = Color.rgb(232, 247, 244);
    private static final int WARNING = Color.rgb(217, 119, 6);
    private static final int WARNING_SOFT = Color.rgb(255, 244, 229);
    private static final int NEUTRAL_SOFT = Color.rgb(244, 244, 245);

    private final Map<String, JSONObject> screens = new HashMap<>();
    private final Map<String, Integer> visibleItemCounts = new HashMap<>();
    private final Map<String, String> selectedCategories = new HashMap<>();
    private final Map<String, String> selectedOfferSections = new HashMap<>();
    private final Map<String, String> selectedMessageDepartments = new HashMap<>();
    private final Map<String, String> searchQueries = new HashMap<>();
    private final Map<String, String> selectedFilters = new HashMap<>();
    private final Map<String, Boolean> expandedFilterSections = new HashMap<>();
    private final Map<String, Boolean> expandedCategorySections = new HashMap<>();
    private final Map<String, Boolean> expandedAccountSections = new HashMap<>();
    private final Handler telemetryHandler = new Handler(Looper.getMainLooper());
    private final Runnable telemetryHeartbeat = new Runnable() {
        @Override
        public void run() {
            trackEvent("heartbeat", currentScreen, null, null);
            telemetryHandler.postDelayed(this, TELEMETRY_HEARTBEAT_MS);
        }
    };
    private ConfigDatabase configDatabase;
    private JSONObject config;
    private ScrollView scrollView;
    private LinearLayout content;
    private LinearLayout nav;
    private Button messagesButton;
    private Button cartButton;
    private String currentScreen = "home";
    private JSONObject activeProgramDetail;
    private JSONObject activeProductDetail;
    private String productReturnScreen = "shop";
    private int activeProductGalleryIndex = 0;
    private int cartCount = 0;
    private int unreadMessageCount = 0;
    private final Map<String, JSONObject> cartItems = new LinkedHashMap<>();
    private boolean cartRefreshInProgress = false;
    private long lastCartRefreshAt = 0L;
    private String telemetryDeviceId;
    private String telemetrySessionId;
    private SharedPreferences customerPreferences;
    private String authToken = "";
    private String customerName = "";
    private String customerPhone = "";
    private String customerEmail = "";
    private String customerAddress = "";
    private String activeManifestUrl = "";
    private Typeface bodyTypeface;
    private Typeface emphasisTypeface;
    private boolean showingRegistrationForm = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeTypography();
        customerPreferences = getSharedPreferences(CUSTOMER_PREFS, MODE_PRIVATE);
        initializeTelemetry();
        activeManifestUrl = customerPreferences.getString(ACTIVE_MANIFEST_URL_PREF, "");
        loadCustomerProfile();
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(TOP_BAR_SURFACE);
        getWindow().setNavigationBarColor(SURFACE);
        int systemUiFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            systemUiFlags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(systemUiFlags);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        showIntroAndStart();
    }

    private void initializeTypography() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            bodyTypeface = getResources().getFont(R.font.vazirmatn_regular);
            emphasisTypeface = getResources().getFont(R.font.vazirmatn_semibold);
        } else {
            bodyTypeface = Typeface.create("sans-serif", Typeface.NORMAL);
            emphasisTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        telemetryHandler.removeCallbacks(telemetryHeartbeat);
        telemetryHandler.postDelayed(telemetryHeartbeat, TELEMETRY_HEARTBEAT_MS);
        refreshMobileState();
    }

    @Override
    protected void onPause() {
        telemetryHandler.removeCallbacks(telemetryHeartbeat);
        super.onPause();
    }

    private void showIntroAndStart() {
        LoadingIntroView introView = new LoadingIntroView(this);
        setContentView(introView);
        introView.start();

        long startedAt = SystemClock.uptimeMillis();
        new Thread(() -> {
            Exception loadError = null;
            try {
                loadConfig();
            } catch (Exception e) {
                loadError = e;
            }

            long remaining = Math.max(0L, INTRO_MIN_DURATION_MS - (SystemClock.uptimeMillis() - startedAt));
            Exception finalLoadError = loadError;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                introView.stop();

                if (finalLoadError != null) {
                    Toast.makeText(this, "خطا در آماده‌سازی برنامه", Toast.LENGTH_LONG).show();
                    throw new IllegalStateException("Cannot load app config", finalLoadError);
                }

                setContentView(buildShell());
                renderScreen(currentScreen, true);
                trackEvent("app_open", currentScreen, null, deviceTelemetryMetadata());
                trackEvent("screen_view", currentScreen, null, null);
                refreshMobileState();
                if (isLoggedIn()) {
                    refreshCustomerScreens();
                }
                checkForConfigUpdate();
            }, remaining);
        }).start();
    }

    private void loadConfig() {
        try {
            configDatabase = new ConfigDatabase(this);
            JSONObject manifest = loadManifest();
            applyManifest(manifest);
            loadScreensFromCache(manifest);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load app config", e);
        }
    }

    private void initializeTelemetry() {
        if (customerPreferences == null) {
            customerPreferences = getSharedPreferences(CUSTOMER_PREFS, MODE_PRIVATE);
        }

        String storedDeviceId = customerPreferences.getString(TELEMETRY_DEVICE_ID_PREF, "");
        if (storedDeviceId.trim().isEmpty()) {
            storedDeviceId = UUID.randomUUID().toString();
            customerPreferences.edit()
                    .putString(TELEMETRY_DEVICE_ID_PREF, storedDeviceId)
                    .apply();
        }

        telemetryDeviceId = storedDeviceId;
        telemetrySessionId = UUID.randomUUID().toString();
    }

    private void loadCustomerProfile() {
        if (customerPreferences == null) return;

        authToken = customerPreferences.getString("auth_token", "");
        customerName = customerPreferences.getString("name", "");
        customerPhone = customerPreferences.getString("phone", "");
        customerEmail = customerPreferences.getString("email", "");
        customerAddress = customerPreferences.getString("address", "");
    }

    private void saveCustomerProfile() {
        if (customerPreferences == null) return;

        customerPreferences.edit()
                .putString("auth_token", authToken == null ? "" : authToken)
                .putString("name", customerName == null ? "" : customerName)
                .putString("phone", customerPhone == null ? "" : customerPhone)
                .putString("email", customerEmail == null ? "" : customerEmail)
                .putString("address", customerAddress == null ? "" : customerAddress)
                .apply();
    }

    private boolean isLoggedIn() {
        return authToken != null && !authToken.trim().isEmpty();
    }

    private String customerNameOrFallback() {
        String value = customerName == null ? "" : customerName.trim();
        return value.isEmpty() ? "Mobile Customer" : value;
    }

    private boolean isMissingCustomerIdentity() {
        return "Mobile Customer".equals(customerNameOrFallback())
                || customerPhone == null
                || customerPhone.trim().isEmpty();
    }

    private void applyUserPayload(JSONObject user) {
        if (user == null) return;

        customerName = user.optString("name", customerName);
        customerEmail = user.optString("email", customerEmail);
        JSONObject profile = user.optJSONObject("profile");
        if (profile != null) {
            customerName = profile.optString("name", customerName);
            customerPhone = profile.optString("phone", customerPhone);
            customerEmail = profile.optString("email", customerEmail);
            customerAddress = profile.optString("delivery_address", customerAddress);
        }
        saveCustomerProfile();
    }

    private void trackAction(String action) {
        trackEvent("action", currentScreen, action, null);
    }

    private void trackAction(String action, JSONObject metadata) {
        trackEvent("action", currentScreen, action, metadata);
    }

    private void trackError(String action, Exception exception) {
        try {
            JSONObject metadata = new JSONObject();
            metadata.put("exception", exception.getClass().getSimpleName());
            metadata.put("message", exception.getMessage() == null ? "" : exception.getMessage());
            trackEvent("error", currentScreen, action, metadata);
        } catch (Exception ignored) {
        }
    }

    private void trackEvent(String eventName, String screenId, String action, JSONObject metadata) {
        String url = telemetryUrl();
        if (url.isEmpty() || telemetryDeviceId == null || telemetryDeviceId.trim().isEmpty()) {
            return;
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("device_id", telemetryDeviceId);
            payload.put("session_id", telemetrySessionId);
            payload.put("platform", "android");
            payload.put("app_version", config == null ? "0" : String.valueOf(config.optInt("appVersion", 0)));
            payload.put("event_name", eventName);
            if (screenId != null && !screenId.trim().isEmpty()) {
                payload.put("screen_id", screenId);
            }
            if (action != null && !action.trim().isEmpty()) {
                payload.put("action", action);
            }
            if (metadata != null) {
                payload.put("metadata", metadata);
            }

            new Thread(() -> {
                try {
                    postJson(url, payload.toString());
                } catch (Exception ignored) {
                }
            }).start();
        } catch (Exception ignored) {
        }
    }

    private JSONObject deviceTelemetryMetadata() {
        JSONObject metadata = new JSONObject();
        try {
            metadata.put("manufacturer", Build.MANUFACTURER);
            metadata.put("model", Build.MODEL);
            metadata.put("sdk", Build.VERSION.SDK_INT);
        } catch (Exception ignored) {
        }
        return metadata;
    }

    private JSONObject metadata(String key, String value) {
        JSONObject metadata = new JSONObject();
        try {
            metadata.put(key, value == null ? "" : value);
        } catch (Exception ignored) {
        }
        return metadata;
    }

    private JSONObject remoteConfig() {
        return config == null ? null : config.optJSONObject("remoteConfig");
    }

    private String telemetryUrl() {
        JSONObject remoteConfig = remoteConfig();
        if (remoteConfig == null) {
            return "";
        }

        String url = withActiveManifestBase(remoteConfig.optString("telemetryUrl", "").trim());
        if (!url.isEmpty()) {
            return url;
        }

        String manifestUrl = withActiveManifestBase(remoteConfig.optString("manifestUrl", "").trim());
        if (manifestUrl.endsWith("/manifest")) {
            return manifestUrl.substring(0, manifestUrl.length() - "/manifest".length()) + "/telemetry";
        }
        return "";
    }

    private JSONObject loadManifest() throws Exception {
        JSONObject bundledManifest = new JSONObject(readAsset(BUNDLED_MANIFEST_PATH));
        validateManifest(bundledManifest);

        String cachedManifest = configDatabase.getManifestJson();
        if (cachedManifest != null) {
            try {
                JSONObject manifest = new JSONObject(cachedManifest);
                validateManifest(manifest);
                if (manifest.optInt("appVersion", 0) >= bundledManifest.optInt("appVersion", 0)) {
                    return manifest;
                }
            } catch (Exception ignored) {
            }
        }

        configDatabase.saveManifest(bundledManifest, bundledManifest.optInt("appVersion", 0));
        return bundledManifest;
    }

    private void applyManifest(JSONObject nextConfig) throws Exception {
        validateManifest(nextConfig);
        config = nextConfig;
    }

    private void loadScreensFromCache(JSONObject manifest) throws Exception {
        screens.clear();
        screens.putAll(loadScreens(manifest));
    }

    private Map<String, JSONObject> loadScreens(JSONObject manifest) throws Exception {
        Map<String, JSONObject> loadedScreens = new HashMap<>();
        JSONObject manifestScreens = manifest.getJSONObject("screens");
        JSONArray ids = manifestScreens.names();
        if (ids == null) return loadedScreens;

        for (int i = 0; i < ids.length(); i++) {
            String screenId = ids.getString(i);
            JSONObject screenMeta = manifestScreens.getJSONObject(screenId);
            JSONObject screen = loadScreen(screenId, screenMeta);
            loadedScreens.put(screenId, screen);
        }
        return loadedScreens;
    }

    private JSONObject loadScreen(String screenId, JSONObject screenMeta) throws Exception {
        ConfigDatabase.ScreenCacheEntry cached = configDatabase.getScreenCache(screenId);
        int bundledVersion = screenMeta.optInt("version", 0);
        if (cached != null && cached.version >= bundledVersion) {
            try {
                JSONObject screen = new JSONObject(cached.rawJson);
                validateScreen(screen, screenId);
                return screen;
            } catch (Exception ignored) {
            }
        }

        String assetPath = screenMeta.optString("asset", "");
        if (assetPath.isEmpty()) {
            throw new IllegalStateException("Missing bundled screen asset: " + screenId);
        }
        JSONObject bundledScreen = new JSONObject(readAsset(assetPath));
        validateScreen(bundledScreen, screenId);
        configDatabase.saveScreen(screenId, bundledScreen, bundledScreen.optInt("version", screenMeta.optInt("version", 0)));
        return bundledScreen;
    }

    private void checkForConfigUpdate() {
        List<String> manifestUrls = manifestUrlCandidates();
        if (manifestUrls.isEmpty()) {
            return;
        }

        new Thread(() -> {
            Exception lastError = null;
            for (String manifestUrl : manifestUrls) {
                try {
                    JSONObject manifest = new JSONObject(downloadText(manifestUrl));
                    validateManifest(manifest);
                    rememberActiveManifestUrl(manifestUrl);
                    int remoteVersion = manifest.optInt("appVersion", 0);
                    int currentVersion = config.optInt("appVersion", 0);
                    if (remoteVersion <= currentVersion) {
                        return;
                    }

                    downloadChangedScreens(manifest);
                    configDatabase.saveManifest(manifest, remoteVersion);
                    Map<String, JSONObject> updatedScreens = loadScreens(manifest);
                    runOnUiThread(() -> {
                        try {
                            applyManifest(manifest);
                            screens.clear();
                            screens.putAll(updatedScreens);
                            if (!screens.containsKey(currentScreen)) {
                                currentScreen = "home";
                            }
                            renderScreen(currentScreen);
                            refreshMobileState();
                            trackEvent("config_update", currentScreen, "manifest_updated", metadata("appVersion", String.valueOf(remoteVersion)));
                        } catch (Exception e) {
                            trackError("config_update_apply", e);
                            Toast.makeText(MainActivity.this, "خطا در به‌روزرسانی تنظیمات", Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                } catch (Exception e) {
                    lastError = e;
                }
            }

            if (lastError != null) {
                trackError("config_update", lastError);
            }
        }).start();
    }

    private List<String> manifestUrlCandidates() {
        List<String> urls = new ArrayList<>();
        addUrlCandidate(urls, activeManifestUrl);

        JSONObject remoteConfig = config == null ? null : config.optJSONObject("remoteConfig");
        if (remoteConfig != null) {
            addUrlCandidate(urls, remoteConfig.optString("manifestUrl", ""));

            JSONArray manifestUrls = remoteConfig.optJSONArray("manifestUrls");
            if (manifestUrls != null) {
                for (int i = 0; i < manifestUrls.length(); i++) {
                    addUrlCandidate(urls, manifestUrls.optString(i, ""));
                }
            }
        }

        List<String> seededUrls = new ArrayList<>(urls);
        for (String url : seededUrls) {
            addLocalUrlAlternates(urls, url);
        }

        return urls;
    }

    private void rememberActiveManifestUrl(String manifestUrl) {
        activeManifestUrl = manifestUrl == null ? "" : manifestUrl.trim();
        if (customerPreferences != null && !activeManifestUrl.isEmpty()) {
            customerPreferences.edit()
                    .putString(ACTIVE_MANIFEST_URL_PREF, activeManifestUrl)
                    .apply();
        }
    }

    private void addUrlCandidate(List<String> urls, String urlValue) {
        String url = urlValue == null ? "" : urlValue.trim();
        if (!url.isEmpty() && !urls.contains(url)) {
            urls.add(url);
        }
    }

    private void addLocalUrlAlternates(List<String> urls, String urlValue) {
        try {
            URL url = new URL(urlValue);
            String host = url.getHost();
            if ("10.0.2.2".equals(host)) {
                addUrlCandidate(urls, replaceUrlHost(url, "127.0.0.1"));
                addUrlCandidate(urls, replaceUrlHost(url, "localhost"));
            } else if ("127.0.0.1".equals(host) || "localhost".equals(host)) {
                addUrlCandidate(urls, replaceUrlHost(url, "10.0.2.2"));
                addUrlCandidate(urls, replaceUrlHost(url, "127.0.0.1".equals(host) ? "localhost" : "127.0.0.1"));
            }
        } catch (Exception ignored) {
        }
    }

    private String replaceUrlHost(URL url, String host) throws Exception {
        return new URL(url.getProtocol(), host, url.getPort(), url.getFile()).toString();
    }

    private String withActiveManifestBase(String urlValue) {
        return withActiveManifestBase(urlValue, config);
    }

    private String withActiveManifestBase(String urlValue, JSONObject manifest) {
        String url = urlValue == null ? "" : urlValue.trim();
        if (url.isEmpty() || activeManifestUrl == null || activeManifestUrl.trim().isEmpty()) {
            return url;
        }

        JSONObject remoteConfig = manifest == null ? null : manifest.optJSONObject("remoteConfig");
        String configuredManifestUrl = remoteConfig == null
                ? ""
                : remoteConfig.optString("manifestUrl", "").trim();
        String configuredBase = apiBaseFromManifestUrl(configuredManifestUrl);
        String activeBase = apiBaseFromManifestUrl(activeManifestUrl);
        if (!configuredBase.isEmpty() && !activeBase.isEmpty() && url.startsWith(configuredBase)) {
            return activeBase + url.substring(configuredBase.length());
        }

        return url;
    }

    private String apiBaseFromManifestUrl(String manifestUrl) {
        String url = manifestUrl == null ? "" : manifestUrl.trim();
        if (url.endsWith("/mobile/manifest")) {
            return url.substring(0, url.length() - "/mobile/manifest".length());
        }
        if (url.endsWith("/manifest")) {
            return url.substring(0, url.length() - "/manifest".length());
        }
        return "";
    }

    private String downloadTextWithFallbacks(String urlValue) throws Exception {
        Exception lastError = null;
        List<String> urls = urlCandidates(urlValue);
        for (String url : urls) {
            try {
                return downloadText(url);
            } catch (Exception e) {
                lastError = e;
            }
        }

        if (lastError != null) {
            throw lastError;
        }
        throw new IllegalStateException("Missing URL");
    }

    private Bitmap downloadBitmapWithFallbacks(String urlValue) throws Exception {
        Exception lastError = null;
        List<String> urls = urlCandidates(urlValue);
        for (String url : urls) {
            try {
                return downloadBitmap(url);
            } catch (Exception e) {
                lastError = e;
            }
        }

        if (lastError != null) {
            throw lastError;
        }
        throw new IllegalStateException("Missing URL");
    }

    private List<String> urlCandidates(String urlValue) {
        List<String> urls = new ArrayList<>();
        addUrlCandidate(urls, withActiveManifestBase(urlValue));
        addUrlCandidate(urls, urlValue);

        List<String> seededUrls = new ArrayList<>(urls);
        for (String url : seededUrls) {
            addLocalUrlAlternates(urls, url);
        }

        return urls;
    }

    private void downloadChangedScreens(JSONObject manifest) throws Exception {
        JSONObject remoteScreens = manifest.getJSONObject("screens");
        JSONArray ids = remoteScreens.names();
        if (ids == null) return;

        for (int i = 0; i < ids.length(); i++) {
            String screenId = ids.getString(i);
            JSONObject screenMeta = remoteScreens.getJSONObject(screenId);
            int remoteVersion = screenMeta.optInt("version", 0);
            int localVersion = configDatabase.getScreenVersion(screenId);
            if (remoteVersion <= localVersion) {
                continue;
            }

            String url = withActiveManifestBase(screenMeta.optString("url", ""), manifest);
            if (url.trim().isEmpty()) {
                continue;
            }

            String screenJson = downloadTextWithFallbacks(url);
            String checksum = screenMeta.optString("checksum", "");
            if (!checksum.isEmpty() && !checksum.equalsIgnoreCase(sha256(screenJson))) {
                continue;
            }

            JSONObject screen = new JSONObject(screenJson);
            validateScreen(screen, screenId);
            configDatabase.saveScreen(screenId, screen, screen.optInt("version", remoteVersion));
        }
    }

    private void validateManifest(JSONObject candidate) throws Exception {
        int schemaVersion = candidate.optInt("schemaVersion", 1);
        if (schemaVersion > SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported schema version");
        }
        candidate.getJSONObject("theme");
        candidate.getJSONArray("navigation");
        JSONObject screenList = candidate.getJSONObject("screens");
        if (screenList.length() == 0) {
            throw new IllegalArgumentException("Config must contain screens");
        }
    }

    private void validateScreen(JSONObject screen, String expectedId) throws Exception {
        int schemaVersion = screen.optInt("schemaVersion", 1);
        if (schemaVersion > SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported screen schema version");
        }
        String screenId = screen.getString("screenId");
        if (!expectedId.equals(screenId)) {
            throw new IllegalArgumentException("Screen id mismatch");
        }
        screen.getString("title");
        screen.getJSONArray("sections");
    }

    private View buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);
        root.setLayoutParams(match());
        root.setPadding(dp(0), dp(0), dp(0), dp(0));

        View topBar = buildTopBar();
        int topBarBasePaddingLeft = topBar.getPaddingLeft();
        int topBarBasePaddingTop = topBar.getPaddingTop();
        int topBarBasePaddingRight = topBar.getPaddingRight();
        int topBarBasePaddingBottom = topBar.getPaddingBottom();
        root.addView(topBar);

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setBackgroundColor(BACKGROUND);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int contentBottomPadding = dp(24);
        content.setPadding(dp(16), dp(12), dp(16), contentBottomPadding);
        scrollView.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        int navBaseHeight = dp(66);
        int navBottomPadding = dp(4);
        nav.setPadding(dp(6), dp(6), dp(6), navBottomPadding);
        nav.setBackground(rounded(SURFACE, 0, BORDER, 1));
        nav.setElevation(dp(8));
        root.addView(nav, new LinearLayout.LayoutParams(-1, navBaseHeight));

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int systemTopInset = insets.getSystemWindowInsetTop();
            int systemBottomInset = insets.getSystemWindowInsetBottom();
            topBar.setPadding(
                    topBarBasePaddingLeft,
                    topBarBasePaddingTop + systemTopInset,
                    topBarBasePaddingRight,
                    topBarBasePaddingBottom
            );
            content.setPadding(dp(16), dp(12), dp(16), contentBottomPadding);
            nav.setPadding(dp(6), dp(6), dp(6), navBottomPadding + systemBottomInset);

            ViewGroup.LayoutParams navParams = nav.getLayoutParams();
            if (navParams != null && navParams.height != navBaseHeight + systemBottomInset) {
                navParams.height = navBaseHeight + systemBottomInset;
                nav.setLayoutParams(navParams);
            }
            return insets;
        });
        root.requestApplyInsets();

        return root;
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(8), dp(16), dp(8));
        bar.setBackground(rounded(TOP_BAR_SURFACE, 0, BORDER, 1));
        bar.setElevation(dp(2));

        TextView logo = text("EtokBike", 20, RED, true);
        logo.setContentDescription("EtokBike، فروشگاه تخصصی دوچرخه");
        bar.addView(logo, new LinearLayout.LayoutParams(0, -2, 1));

        messagesButton = topIconButton(R.drawable.ic_message_24);
        updateMessageButton();
        messagesButton.setOnClickListener(v -> {
            trackAction("top_messages");
            openScreen("messages");
        });
        bar.addView(messagesButton, new LinearLayout.LayoutParams(dp(44), dp(44)));

        View actionSpacer = new View(this);
        bar.addView(actionSpacer, new LinearLayout.LayoutParams(dp(6), 1));

        cartButton = topIconButton(R.drawable.ic_cart_24);
        updateCartButton();
        cartButton.setOnClickListener(v -> {
            trackAction("top_cart");
            openScreen("cart");
        });
        LinearLayout.LayoutParams cartParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        bar.addView(cartButton, cartParams);
        return bar;
    }

    private void renderNavigation() {
        nav.removeAllViews();
        try {
            JSONArray items = config.getJSONArray("navigation");
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String label = item.getString("label");
                String screen = item.optString("screenId", item.optString("screen", ""));
                boolean selected = screen.equals(navigationScreenForSelection());
                TextView tab = text(label, 11, selected ? RED : MUTED, selected);
                tab.setGravity(Gravity.CENTER);
                tab.setSingleLine(true);
                tab.setEllipsize(TextUtils.TruncateAt.END);
                tab.setFocusable(true);
                tab.setSelected(selected);
                tab.setMinHeight(dp(52));
                tab.setPadding(dp(4), dp(4), dp(4), dp(3));
                tab.setCompoundDrawablesWithIntrinsicBounds(0, navigationIconResource(screen), 0, 0);
                tab.setCompoundDrawablePadding(dp(2));
                tab.setCompoundDrawableTintList(ColorStateList.valueOf(selected ? RED : MUTED));
                tab.setContentDescription(selected ? label + "، فعال" : label);
                tab.setBackground(interactiveBackground(selected ? RED_TINT : SURFACE, 14, selected ? HERO_BORDER : SURFACE, selected ? 1 : 0));
                attachPressAnimation(tab);
                tab.setOnClickListener(v -> {
                    trackAction("bottom_navigation", metadata("target", screen));
                    openScreen(screen);
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
                params.setMargins(dp(2), dp(0), dp(2), dp(0));
                nav.addView(tab, params);
            }
        } catch (Exception e) {
            trackError("render_navigation", e);
            Toast.makeText(this, "خطا در نمایش ناوبری", Toast.LENGTH_SHORT).show();
        }
    }

    private int navigationIconResource(String screen) {
        switch (screen) {
            case "shop":
                return R.drawable.ic_store_24;
            case "services":
                return R.drawable.ic_service_24;
            case "events":
                return R.drawable.ic_events_24;
            case "account":
                return R.drawable.ic_account_24;
            case "home":
            default:
                return R.drawable.ic_home_24;
        }
    }

    private void renderScreen(String screenId) {
        boolean resetScroll = !screenId.equals(currentScreen);
        renderScreen(screenId, resetScroll);
    }

    private void openScreen(String screenId) {
        if (screenId.equals(currentScreen)) {
            scrollToTop();
        } else {
            renderScreen(screenId);
        }
    }

    private void renderScreen(String screenId, boolean resetScroll) {
        if ("product-detail".equals(screenId)) {
            renderProductDetailScreen();
            return;
        }

        if ("program-detail".equals(screenId)) {
            renderProgramDetailScreen();
            return;
        }

        JSONObject screen = screens.get(screenId);
        if (screen == null) return;

        String previousScreen = currentScreen;
        currentScreen = screenId;
        content.removeAllViews();
        renderNavigation();
        if (!screenId.equals(previousScreen)) {
            trackEvent("screen_view", screenId, "navigate", null);
        }

        try {
            if (!screen.optBoolean("hideTitle", false)) {
                TextView title = text(screen.getString("title"), 28, BLACK, true);
                title.setGravity(Gravity.START);
                content.addView(title, new LinearLayout.LayoutParams(-1, -2));
                addSpace(content, 14);
            }

            if ("account".equals(screenId)) {
                content.addView(accountAccessPanel(), new LinearLayout.LayoutParams(-1, -2));
                addSpace(content, 14);
                if (!isLoggedIn()) {
                    if (resetScroll) {
                        scrollToTop();
                    }
                    animateScreenEntrance(resetScroll);
                    return;
                }
            }

            JSONArray sections = screen.getJSONArray("sections");
            for (int i = 0; i < sections.length(); i++) {
                renderSection(sections.getJSONObject(i));
                addSpace(content, 14);
            }
            if (resetScroll) {
                scrollToTop();
            }
            animateScreenEntrance(resetScroll);
        } catch (Exception e) {
            trackError("render_screen", e);
            Toast.makeText(this, "خطا در نمایش صفحه", Toast.LENGTH_SHORT).show();
        }
    }

    private View accountAccessPanel() {
        if (isLoggedIn()) {
            return connectedAccountPanel();
        }
        return showingRegistrationForm ? registrationPanel() : loginPanel();
    }

    private LinearLayout accountPanel(String title, String subtitle) {
        LinearLayout card = panel(WHITE);
        card.addView(text(title, 19, BLACK, true), new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 6);
        card.addView(text(subtitle, 13, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 16);
        return card;
    }

    private View loginPanel() {
        LinearLayout card = accountPanel(
                "ورود به حساب",
                "برای دیدن سفارش‌ها، پیام‌ها و وضعیت سرویس با ایمیل خود وارد شوید."
        );

        EditText email = addTextField(card, "ایمیل", customerEmail, false, 1);
        addSpace(card, 10);
        EditText password = addTextField(card, "رمز عبور", "", true, 1);
        password.setImeOptions(EditorInfo.IME_ACTION_DONE);
        addSpace(card, 16);

        Button login = button("ورود", true);
        login.setOnClickListener(v -> loginAccount(email.getText().toString().trim(), password.getText().toString()));
        card.addView(login, new LinearLayout.LayoutParams(-1, dp(48)));
        addSpace(card, 8);

        Button register = button("حساب ندارید؟ ساخت حساب", false);
        register.setOnClickListener(v -> {
            showingRegistrationForm = true;
            renderScreen("account", false);
            scrollToTop();
        });
        card.addView(register, new LinearLayout.LayoutParams(-1, dp(48)));
        return card;
    }

    private View registrationPanel() {
        LinearLayout card = accountPanel(
                "ساخت حساب",
                "اطلاعات اصلی را وارد کنید؛ شماره تماس و آدرس تحویل را بعداً هم می‌توانید تغییر دهید."
        );

        EditText name = addTextField(card, "نام", customerName, false, 1);
        addSpace(card, 10);
        EditText phone = addTextField(card, "شماره تماس", customerPhone, false, 1);
        addSpace(card, 10);
        EditText email = addTextField(card, "ایمیل", customerEmail, false, 1);
        addSpace(card, 10);
        EditText address = addTextField(card, "آدرس تحویل", customerAddress, false, 2);
        addSpace(card, 10);
        EditText password = addTextField(card, "رمز عبور", "", true, 1);
        password.setHint("حداقل ۸ کاراکتر");
        password.setImeOptions(EditorInfo.IME_ACTION_DONE);
        addSpace(card, 16);

        Button register = button("ساخت حساب", true);
        register.setOnClickListener(v -> registerAccount(name, phone, email, address, password));
        card.addView(register, new LinearLayout.LayoutParams(-1, dp(48)));
        addSpace(card, 8);

        Button login = button("قبلاً ثبت‌نام کرده‌اید؟ ورود", false);
        login.setOnClickListener(v -> {
            showingRegistrationForm = false;
            renderScreen("account", false);
            scrollToTop();
        });
        card.addView(login, new LinearLayout.LayoutParams(-1, dp(48)));
        return card;
    }

    private View connectedAccountPanel() {
        LinearLayout card = accountPanel(
                "حساب متصل",
                "اطلاعات تماس و تحویل برای سفارش‌ها، رزرو سرویس و پیام‌های شما استفاده می‌شود."
        );

        EditText name = addTextField(card, "نام", customerName, false, 1);
        addSpace(card, 10);
        EditText phone = addTextField(card, "شماره تماس", customerPhone, false, 1);
        addSpace(card, 10);
        EditText email = addTextField(card, "ایمیل", customerEmail, false, 1);
        addSpace(card, 10);
        EditText address = addTextField(card, "آدرس تحویل", customerAddress, false, 2);
        addSpace(card, 16);

        Button update = button("ذخیره تغییرات", true);
        update.setOnClickListener(v -> updateAccountProfile(name, phone, email, address));
        card.addView(update, new LinearLayout.LayoutParams(-1, dp(48)));
        addSpace(card, 8);

        Button logout = button("خروج از حساب", false);
        logout.setTextColor(RED);
        logout.setOnClickListener(v -> {
            authToken = "";
            saveCustomerProfile();
            cartItems.clear();
            cartCount = 0;
            unreadMessageCount = 0;
            showingRegistrationForm = false;
            updateCartButton();
            updateMessageButton();
            Toast.makeText(this, "از حساب خارج شدید", Toast.LENGTH_SHORT).show();
            renderScreen("account", false);
        });
        card.addView(logout, new LinearLayout.LayoutParams(-1, dp(48)));
        return card;
    }

    private EditText addTextField(LinearLayout parent, String label, String value, boolean password, int minLines) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.START);
        box.addView(text(label, 13, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        addSpace(box, 4);

        EditText input = new EditText(this);
        input.setText(value == null ? "" : value);
        input.setTextSize(14);
        input.setTextColor(BLACK);
        input.setHintTextColor(MUTED);
        input.setTypeface(bodyTypeface);
        input.setGravity(Gravity.START);
        input.setSingleLine(minLines <= 1);
        input.setMinLines(minLines);
        input.setBackground(rounded(SURFACE_ALT, 10, BORDER, 1));
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        input.setContentDescription(label);
        input.setImeOptions(password ? EditorInfo.IME_ACTION_DONE : EditorInfo.IME_ACTION_NEXT);
        if (password) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else if (label.contains("ایمیل")) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            input.setTextDirection(View.TEXT_DIRECTION_LTR);
        } else if (label.contains("تماس")) {
            input.setInputType(InputType.TYPE_CLASS_PHONE);
            input.setTextDirection(View.TEXT_DIRECTION_LTR);
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT | (minLines > 1 ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        }
        box.addView(input, new LinearLayout.LayoutParams(-1, minLines > 1 ? dp(84) : dp(48)));
        parent.addView(box, new LinearLayout.LayoutParams(-1, -2));
        return input;
    }

    private Spinner addChoiceField(LinearLayout parent, String label, String[] options) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.START);
        box.addView(text(label, 13, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        addSpace(box, 4);

        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        box.addView(spinner, new LinearLayout.LayoutParams(-1, dp(48)));
        parent.addView(box, new LinearLayout.LayoutParams(-1, -2));
        return spinner;
    }

    private String paymentMethodValue(int position) {
        if (position == 1) return "cash_on_delivery";
        if (position == 2) return "bank_transfer";
        return "pay_in_store";
    }

    private void saveCustomerInputs(EditText name, EditText phone, EditText email, EditText address) {
        customerName = name.getText().toString().trim();
        customerPhone = phone.getText().toString().trim();
        customerEmail = email.getText().toString().trim();
        customerAddress = address.getText().toString().trim();
        saveCustomerProfile();
    }

    private void loginAccount(String email, String password) {
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "ایمیل و رمز عبور را وارد کنید", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = remoteUrl("loginUrl", "/auth/login");
        if (url.isEmpty()) {
            Toast.makeText(this, "آدرس ورود تنظیم نشده است", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("email", email);
            payload.put("password", password);
            payload.put("device_name", "android");
            submitAuthRequest(url, payload, "ورود انجام شد");
        } catch (Exception e) {
            trackError("login_payload", e);
            Toast.makeText(this, "خطا در ورود", Toast.LENGTH_SHORT).show();
        }
    }

    private void registerAccount(EditText name, EditText phone, EditText email, EditText address, EditText password) {
        String nameValue = name.getText().toString().trim();
        String emailValue = email.getText().toString().trim();
        String passwordValue = password.getText().toString();

        if (nameValue.isEmpty() || emailValue.isEmpty() || passwordValue.length() < 8) {
            Toast.makeText(this, "نام، ایمیل و رمز حداقل ۸ کاراکتری لازم است", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = remoteUrl("registerUrl", "/auth/register");
        if (url.isEmpty()) {
            Toast.makeText(this, "آدرس ثبت نام تنظیم نشده است", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("name", nameValue);
            payload.put("email", emailValue);
            payload.put("password", passwordValue);
            payload.put("phone", phone.getText().toString().trim());
            payload.put("delivery_address", address.getText().toString().trim());
            payload.put("device_name", "android");
            submitAuthRequest(url, payload, "ثبت نام انجام شد");
        } catch (Exception e) {
            trackError("register_payload", e);
            Toast.makeText(this, "خطا در ثبت نام", Toast.LENGTH_SHORT).show();
        }
    }

    private void submitAuthRequest(String url, JSONObject payload, String successMessage) {
        new Thread(() -> {
            try {
                String responseText = postJson(url, payload.toString());
                JSONObject data = new JSONObject(responseText).optJSONObject("data");
                if (data == null) throw new IllegalStateException("Missing auth data");

                authToken = data.optString("token", authToken);
                applyUserPayload(data.optJSONObject("user"));
                runOnUiThread(() -> {
                    Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show();
                    refreshCustomerScreens();
                    refreshMobileState();
                    renderScreen("account", false);
                });
            } catch (Exception e) {
                trackError("auth_request", e);
                runOnUiThread(() -> Toast.makeText(this, "خطا در عملیات حساب", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void updateAccountProfile(EditText name, EditText phone, EditText email, EditText address) {
        saveCustomerInputs(name, phone, email, address);

        String url = remoteUrl("accountUpdateUrl", "/account");
        if (url.isEmpty() || !isLoggedIn()) {
            Toast.makeText(this, "اطلاعات محلی ذخیره شد", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("name", customerNameOrFallback());
            payload.put("phone", customerPhone);
            payload.put("email", customerEmail);
            payload.put("delivery_address", customerAddress);

            new Thread(() -> {
                try {
                    JSONObject response = new JSONObject(patchJson(url, payload.toString()));
                    JSONObject data = response.optJSONObject("data");
                    if (data != null && data.optJSONObject("profile") != null) {
                        JSONObject user = new JSONObject();
                        user.put("name", customerName);
                        user.put("email", customerEmail);
                        user.put("profile", data.optJSONObject("profile"));
                        applyUserPayload(user);
                    }
                    runOnUiThread(() -> {
                        Toast.makeText(this, "حساب به‌روزرسانی شد", Toast.LENGTH_SHORT).show();
                        refreshCustomerScreens();
                    });
                } catch (Exception e) {
                    trackError("account_update", e);
                    runOnUiThread(() -> Toast.makeText(this, "خطا در به‌روزرسانی حساب", Toast.LENGTH_SHORT).show());
                }
            }).start();
        } catch (Exception e) {
            trackError("account_update_payload", e);
            Toast.makeText(this, "خطا در به‌روزرسانی حساب", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshCustomerScreens() {
        reloadRemoteScreen("account");
        reloadRemoteScreen("messages");
        reloadRemoteScreen("home");
        reloadRemoteScreen("services");
    }

    private void reloadRemoteScreen(String screenId) {
        JSONObject screenConfigs = config == null ? null : config.optJSONObject("screens");
        if (screenConfigs == null) return;

        JSONObject screenMeta = screenConfigs.optJSONObject(screenId);
        if (screenMeta == null) return;

        String url = screenMeta.optString("url", "").trim();
        if (url.isEmpty()) return;

        new Thread(() -> {
            try {
                String rawJson = downloadTextWithFallbacks(url);
                JSONObject screen = new JSONObject(rawJson);
                validateScreen(screen, screenId);
                configDatabase.saveScreen(screenId, screen, screen.optInt("version", screenMeta.optInt("version", 0)));
                runOnUiThread(() -> {
                    screens.put(screenId, screen);
                    if (screenId.equals(currentScreen)) {
                        renderScreen(screenId, false);
                    }
                });
            } catch (Exception e) {
                trackError("reload_screen_" + screenId, e);
            }
        }).start();
    }

    private void renderProgramDetailScreen() {
        if (activeProgramDetail == null) {
            renderScreen("events");
            return;
        }

        String previousScreen = currentScreen;
        currentScreen = "program-detail";
        content.removeAllViews();
        renderNavigation();
        if (!"program-detail".equals(previousScreen)) {
            trackEvent("screen_view", "program-detail", "program_detail", null);
        }

        try {
            Button back = button("بازگشت به برنامه‌ها", false);
            back.setOnClickListener(v -> {
                trackAction("program_detail_back");
                renderScreen("events");
            });
            content.addView(back, new LinearLayout.LayoutParams(-1, dp(44)));
            addSpace(content, 12);

            TextView title = text(activeProgramDetail.getString("title"), 28, BLACK, true);
            title.setGravity(Gravity.START);
            content.addView(title, new LinearLayout.LayoutParams(-1, -2));
            addSpace(content, 14);

            content.addView(programDetail(activeProgramDetail));
            JSONArray programGallery = activeProgramDetail.optJSONArray("gallery");
            if (isFinishedProgram(activeProgramDetail) && programGallery != null && programGallery.length() > 0) {
                addSpace(content, 14);
                content.addView(sectionTitle(activeProgramDetail.optString("galleryTitle", "گالری برنامه")));
                content.addView(gallery(programGallery));
            }
            scrollToTop();
            animateScreenEntrance(!"program-detail".equals(previousScreen));
        } catch (Exception e) {
            trackError("render_program_detail", e);
            Toast.makeText(this, "خطا در نمایش برنامه", Toast.LENGTH_SHORT).show();
        }
    }

    private void renderProductDetailScreen() {
        if (activeProductDetail == null) {
            renderScreen(productReturnScreen == null ? "shop" : productReturnScreen);
            return;
        }

        String previousScreen = currentScreen;
        currentScreen = "product-detail";
        content.removeAllViews();
        renderNavigation();
        if (!"product-detail".equals(previousScreen)) {
            trackEvent("screen_view", "product-detail", "product_detail", metadata("product", activeProductDetail.optString("id", activeProductDetail.optString("title", ""))));
        }

        try {
            Button back = button(productReturnScreenLabel(), false);
            back.setOnClickListener(v -> {
                trackAction("product_detail_back");
                renderScreen(productReturnScreen == null ? "shop" : productReturnScreen);
            });
            content.addView(back, new LinearLayout.LayoutParams(-1, dp(44)));
            addSpace(content, 12);

            content.addView(productDetailGallery(activeProductDetail), new LinearLayout.LayoutParams(-1, -2));
            addSpace(content, 12);
            content.addView(productBuyingPanel(activeProductDetail), new LinearLayout.LayoutParams(-1, -2));
            addSpace(content, 12);
            content.addView(productSpecsPanel(activeProductDetail), new LinearLayout.LayoutParams(-1, -2));
            addSpace(content, 12);
            content.addView(productSupportPanel(), new LinearLayout.LayoutParams(-1, -2));

            scrollToTop();
            animateScreenEntrance(!"product-detail".equals(previousScreen));
        } catch (Exception e) {
            trackError("render_product_detail", e);
            Toast.makeText(this, "خطا در نمایش محصول", Toast.LENGTH_SHORT).show();
        }
    }

    private void scrollToTop() {
        if (scrollView != null) {
            scrollView.post(() -> scrollView.scrollTo(0, 0));
        }
    }

    private String navigationScreenForSelection() {
        if ("program-detail".equals(currentScreen)) {
            return "events";
        }
        if ("product-detail".equals(currentScreen)) {
            return productReturnScreen == null || productReturnScreen.trim().isEmpty() ? "shop" : productReturnScreen;
        }
        return currentScreen;
    }

    private void renderSection(JSONObject section) throws Exception {
        String type = section.getString("type");
        JSONObject data = sectionData(section);
        JSONObject layout = section.optJSONObject("layout");
        String presentation = layout == null ? "" : layout.optString("presentation", layout.optString("content", ""));
        switch (type) {
            case "hero":
                content.addView(hero(data));
                break;
            case "category_grid":
                content.addView(sectionTitle(data.getString("title")));
                content.addView(grid(data.getJSONArray("items")));
                break;
            case "product_row":
                content.addView(sectionTitle(data.getString("title")));
                content.addView(horizontalCards(data.getJSONArray("items")));
                break;
            case "offer_sections":
                content.addView(offerSections(data));
                break;
            case "program_sections":
                content.addView(programSections(section));
                break;
            case "product_list":
                if (!data.optBoolean("hideTitle", false)) {
                    content.addView(sectionTitle(data.getString("title")));
                }
                content.addView(productList(data));
                break;
            case "service_list":
            case "schedule_list":
            case "activity_list":
                content.addView(sectionTitle(data.getString("title")));
                if ("carousel".equals(presentation)) {
                    content.addView(horizontalCards(data.getJSONArray("items")));
                } else {
                    content.addView(listCards(data.getJSONArray("items")));
                }
                break;
            case "client_details":
                content.addView(sectionTitle(data.getString("title")));
                content.addView(clientDetails(data));
                break;
            case "purchase_history":
                content.addView(sectionTitle(data.getString("title")));
                content.addView(accountList(data.getJSONArray("items")));
                break;
            case "ongoing_purchase":
                content.addView(sectionTitle(data.getString("title")));
                content.addView(ongoingPurchases(section));
                break;
            case "message_center":
                content.addView(messageCenter(section));
                break;
            case "cart_summary":
                content.addView(cartSummary(data));
                break;
            case "service_booking_form":
                content.addView(sectionTitle(data.getString("title")));
                content.addView(serviceBookingForm(data));
                break;
            case "status_tracker":
                content.addView(sectionTitle(data.getString("title")));
                content.addView(statusTrackers(section));
                break;
            case "bike_profile_list":
                content.addView(sectionTitle(data.getString("title")));
                content.addView(bikeProfiles(data.getJSONArray("items")));
                break;
            case "business_info":
                content.addView(sectionTitle(data.getString("title")));
                content.addView(businessInfo(data.getJSONArray("items")));
                break;
            case "checkout_note":
            case "profile_summary":
                content.addView(infoPanel(data));
                break;
            default:
                break;
        }
    }

    private JSONObject sectionData(JSONObject section) {
        JSONObject data = section.optJSONObject("data");
        return data == null ? section : data;
    }

    private View hero(JSONObject section) throws Exception {
        if (section.has("primaryActionLabel") || section.has("stats") || section.has("featureTitle")) {
            return bikeShopHero(section);
        }

        LinearLayout box = panel(HERO_SURFACE);
        box.setBackground(rounded(HERO_SURFACE, 12, HERO_BORDER, 1));
        box.setPadding(dp(18), dp(18), dp(18), dp(18));
        box.addView(text(section.getString("title"), 22, BLACK, true), new LinearLayout.LayoutParams(-1, -2));
        addSpace(box, 6);
        box.addView(text(section.getString("subtitle"), 14, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        addSpace(box, 12);
        Button action = button(section.getString("actionLabel"), true);
        String target = section.optString("target", "shop");
        action.setOnClickListener(v -> {
            trackAction("hero_primary", metadata("target", target));
            openScreen(target);
        });
        box.addView(action, new LinearLayout.LayoutParams(-1, dp(46)));
        return box;
    }

    private View bikeShopHero(JSONObject section) throws Exception {
        LinearLayout box = panel(HERO_SURFACE);
        box.setBackground(rounded(HERO_SURFACE, 14, HERO_BORDER, 1));
        box.setPadding(dp(16), dp(16), dp(16), dp(16));

        String eyebrow = section.optString("eyebrow", "");
        if (!eyebrow.isEmpty()) {
            TextView chip = pillText(eyebrow, 12, RED, RED_TINT, DARK_RED);
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(-2, dp(30));
            chipParams.setMargins(dp(0), dp(0), dp(0), dp(6));
            box.addView(chip, chipParams);
        }

        TextView title = text(section.getString("title"), 23, BLACK, true);
        title.setMaxLines(3);
        box.addView(title, new LinearLayout.LayoutParams(-1, -2));
        addSpace(box, 4);

        TextView subtitle = text(section.getString("subtitle"), 13, MUTED, false);
        subtitle.setMaxLines(3);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        box.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

        addSpace(box, 10);
        ImageView bike = new ImageView(this);
        bike.setImageResource(heroVisualResource(section.optString("visual", "bike")));
        bike.setContentDescription(section.optString("visualDescription", "تصویر دوچرخه EtokBike"));
        bike.setAdjustViewBounds(true);
        bike.setScaleType(ImageView.ScaleType.FIT_CENTER);
        bike.setBackground(rounded(HERO_PANEL, 10, HERO_BORDER, 1));
        bike.setPadding(dp(8), dp(8), dp(8), dp(8));

        String featureTitle = section.optString("featureTitle", "");
        boolean splitShowcase = !featureTitle.isEmpty()
                && getResources().getConfiguration().screenWidthDp >= 360;
        if (splitShowcase) {
            LinearLayout showcase = new LinearLayout(this);
            showcase.setOrientation(LinearLayout.HORIZONTAL);
            showcase.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams mediaParams = new LinearLayout.LayoutParams(0, dp(132), 1);
            mediaParams.setMargins(dp(4), dp(0), dp(0), dp(0));
            showcase.addView(bike, mediaParams);
            LinearLayout.LayoutParams featureParams = new LinearLayout.LayoutParams(0, dp(132), 1);
            featureParams.setMargins(dp(0), dp(0), dp(4), dp(0));
            showcase.addView(heroFeaturePanel(section), featureParams);
            box.addView(showcase, new LinearLayout.LayoutParams(-1, -2));
        } else {
            box.addView(bike, new LinearLayout.LayoutParams(-1, dp(124)));
        }

        if (!featureTitle.isEmpty() && !splitShowcase) {
            addSpace(box, 10);
            box.addView(heroFeaturePanel(section), new LinearLayout.LayoutParams(-1, -2));
        }

        JSONArray stats = section.optJSONArray("stats");
        if (stats != null && stats.length() > 0) {
            addSpace(box, 10);
            box.addView(heroStats(stats), new LinearLayout.LayoutParams(-1, -2));
        }

        addSpace(box, 10);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button primary = button(section.optString("primaryActionLabel", section.optString("actionLabel", "مشاهده فروشگاه")), true);
        primary.setOnClickListener(v -> {
            String target = section.optString("primaryTarget", section.optString("target", "shop"));
            trackAction("hero_primary", metadata("target", target));
            openScreen(target);
        });
        Button secondary = button(section.optString("secondaryActionLabel", "رزرو سرویس"), false);
        secondary.setTextColor(RED);
        secondary.setBackground(interactiveBackground(HERO_PANEL, 10, RED, 1));
        secondary.setOnClickListener(v -> {
            String target = section.optString("secondaryTarget", "services");
            trackAction("hero_secondary", metadata("target", target));
            openScreen(target);
        });
        LinearLayout.LayoutParams primaryParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        primaryParams.setMargins(dp(4), dp(0), dp(0), dp(0));
        LinearLayout.LayoutParams secondaryParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        secondaryParams.setMargins(dp(0), dp(0), dp(4), dp(0));
        actions.addView(primary, primaryParams);
        actions.addView(secondary, secondaryParams);
        box.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }

    private View heroFeaturePanel(JSONObject section) {
        LinearLayout feature = new LinearLayout(this);
        feature.setOrientation(LinearLayout.VERTICAL);
        feature.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        feature.setPadding(dp(12), dp(10), dp(12), dp(10));
        feature.setBackground(rounded(HERO_PANEL, 10, HERO_BORDER, 1));

        TextView title = text(section.optString("featureTitle", ""), 14, BLACK, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        feature.addView(title, new LinearLayout.LayoutParams(-1, -2));

        String subtitle = section.optString("featureSubtitle", "");
        if (!subtitle.isEmpty()) {
            addSpace(feature, 3);
            TextView subtitleView = text(subtitle, 11, MUTED, false);
            subtitleView.setMaxLines(2);
            subtitleView.setEllipsize(TextUtils.TruncateAt.END);
            feature.addView(subtitleView, new LinearLayout.LayoutParams(-1, -2));
        }

        String price = section.optString("featurePrice", "");
        if (!price.isEmpty()) {
            addSpace(feature, 4);
            TextView priceView = text(price, 12, ACCENT, true);
            priceView.setMaxLines(1);
            priceView.setEllipsize(TextUtils.TruncateAt.END);
            feature.addView(priceView, new LinearLayout.LayoutParams(-1, -2));
        }
        return feature;
    }

    private View heroStats(JSONArray stats) throws Exception {
        if (stats.length() <= 3) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            for (int i = 0; i < stats.length(); i++) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(62), 1);
                params.setMargins(dp(3), dp(0), dp(3), dp(0));
                row.addView(heroStat(stats.getJSONObject(i)), params);
            }
            return row;
        }

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setHorizontalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(18));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.START);
        for (int i = 0; i < stats.length(); i++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(116), dp(62));
            params.setMargins(dp(4), dp(2), dp(4), dp(2));
            row.addView(heroStat(stats.getJSONObject(i)), params);
        }
        scroll.addView(row);
        return scroll;
    }

    private View heroStat(JSONObject item) throws Exception {
        LinearLayout stat = new LinearLayout(this);
        stat.setOrientation(LinearLayout.VERTICAL);
        stat.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        stat.setPadding(dp(10), dp(7), dp(10), dp(7));
        stat.setBackground(rounded(HERO_PANEL, 10, HERO_BORDER, 1));
        TextView value = text(item.getString("value"), 13, BLACK, true);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        stat.addView(value, new LinearLayout.LayoutParams(-1, -2));
        addSpace(stat, 1);
        TextView label = text(item.getString("label"), 10, MUTED, false);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        stat.addView(label, new LinearLayout.LayoutParams(-1, -2));
        return stat;
    }

    private int heroVisualResource(String visual) {
        if ("service".equals(visual)) return R.drawable.hero_service_shop;
        if ("events".equals(visual)) return R.drawable.hero_events_shop;
        if ("account".equals(visual)) return R.drawable.hero_account_shop;
        if ("messages".equals(visual)) return R.drawable.hero_messages_shop;
        if ("cart".equals(visual)) return R.drawable.hero_cart_shop;
        return R.drawable.hero_bike_shop;
    }

    private View grid(JSONArray items) throws Exception {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < items.length(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            for (int j = 0; j < 2 && i + j < items.length(); j++) {
                JSONObject item = items.getJSONObject(i + j);
                View card = smallCard(item);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(118), 1);
                params.setMargins(dp(5), dp(5), dp(5), dp(5));
                row.addView(card, params);
            }
            wrap.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        return wrap;
    }

    private View horizontalCards(JSONArray items) throws Exception {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < items.length(); i++) {
            View card = productCard(items.getJSONObject(i), false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(CARD_MEDIA_WIDTH_DP), -2);
            params.setMargins(dp(7), dp(5), dp(7), dp(5));
            row.addView(card, params);
        }
        scroll.addView(row);
        return scroll;
    }

    private View offerSections(JSONObject section) throws Exception {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        String key = currentScreen + ":" + section.optString("title", "offers");

        wrap.addView(sectionTitle(section.getString("title")));
        JSONArray subsections = section.getJSONArray("subsections");
        String selected = selectedOfferSections.containsKey(key)
                ? selectedOfferSections.get(key)
                : section.optString("defaultSubsection", subsections.getJSONObject(0).getString("id"));
        wrap.addView(offerSubsectionTabs(subsections, key, selected));
        addSpace(wrap, 8);

        JSONObject selectedSubsection = subsections.getJSONObject(0);
        for (int i = 0; i < subsections.length(); i++) {
            JSONObject subsection = subsections.getJSONObject(i);
            if (subsection.getString("id").equals(selected)) {
                selectedSubsection = subsection;
                break;
            }
        }

        wrap.addView(horizontalCards(selectedSubsection.getJSONArray("items")));
        return wrap;
    }

    private View offerSubsectionTabs(JSONArray subsections, String key, String selected) throws Exception {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        for (int i = 0; i < subsections.length(); i++) {
            JSONObject subsection = subsections.getJSONObject(i);
            String id = subsection.getString("id");
            boolean active = id.equals(selected);
            TextView tab = text(subsection.getString("label"), 14, active ? WHITE : BLACK, true);
            tab.setGravity(Gravity.CENTER);
            tab.setSingleLine(true);
            tab.setEllipsize(TextUtils.TruncateAt.END);
            tab.setPadding(dp(16), dp(9), dp(16), dp(9));
            tab.setFocusable(true);
            tab.setContentDescription(active ? subsection.getString("label") + "، فعال" : subsection.getString("label"));
            tab.setBackground(interactiveBackground(active ? RED : SURFACE, 22, active ? RED : BORDER, 1));
            attachPressAnimation(tab);
            tab.setOnClickListener(v -> {
                trackAction("offer_tab", metadata("subsection", id));
                selectedOfferSections.put(key, id);
                renderScreen(currentScreen);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(42));
            params.setMargins(dp(4), dp(2), dp(4), dp(2));
            row.addView(tab, params);
        }

        scroll.addView(row);
        return scroll;
    }

    private View programSections(JSONObject section) throws Exception {
        JSONObject data = sectionData(section);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        String key = currentScreen + ":" + section.optString("id", data.optString("title", "programs"));

        wrap.addView(sectionTitle(data.getString("title")));
        JSONArray subsections = data.getJSONArray("subsections");
        String selected = selectedOfferSections.containsKey(key)
                ? selectedOfferSections.get(key)
                : data.optString("defaultSubsection", subsections.getJSONObject(0).getString("id"));
        wrap.addView(offerSubsectionTabs(subsections, key, selected));
        addSpace(wrap, 8);

        JSONObject selectedSubsection = subsections.getJSONObject(0);
        for (int i = 0; i < subsections.length(); i++) {
            JSONObject subsection = subsections.getJSONObject(i);
            if (subsection.getString("id").equals(selected)) {
                selectedSubsection = subsection;
                break;
            }
        }

        List<JSONObject> items = sortedPrograms(selectedSubsection.getJSONArray("items"));
        wrap.addView(programCards(items));

        return wrap;
    }

    private List<JSONObject> sortedPrograms(JSONArray source) throws Exception {
        List<JSONObject> items = new ArrayList<>();
        for (int i = 0; i < source.length(); i++) {
            items.add(source.getJSONObject(i));
        }
        Collections.sort(items, (left, right) -> {
            boolean leftFuture = isFutureProgram(left);
            boolean rightFuture = isFutureProgram(right);
            if (leftFuture != rightFuture) {
                return leftFuture ? -1 : 1;
            }
            int compare = left.optString("dateValue", "").compareTo(right.optString("dateValue", ""));
            return leftFuture ? compare : -compare;
        });
        return items;
    }

    private View programCards(List<JSONObject> items) throws Exception {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < items.size(); i++) {
            View card = programCard(items.get(i));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(dp(0), dp(5), dp(0), dp(7));
            list.addView(card, params);
        }
        return list;
    }

    private View programCard(JSONObject item) throws Exception {
        LinearLayout card = panel(WHITE);
        card.setBackground(interactiveBackground(WHITE, 8, BORDER, 1));
        String id = item.getString("id");
        card.setFocusable(true);
        attachPressAnimation(card);
        card.setOnClickListener(v -> {
            trackAction("program_open", metadata("program", id));
            activeProgramDetail = item;
            renderScreen("program-detail");
        });

        card.addView(thumbnail(item, dp(FEATURED_CARD_MEDIA_HEIGHT_DP)), new LinearLayout.LayoutParams(-1, dp(FEATURED_CARD_MEDIA_HEIGHT_DP)));
        addSpace(card, 9);
        TextView title = text(item.getString("title"), 16, BLACK, true);
        title.setMaxLines(2);
        card.addView(title, new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 4);
        card.addView(text(item.getString("dateLabel"), 13, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 4);
        card.addView(text(item.optString("statusLabel", ""), 13, isFutureProgram(item) ? ACCENT : MUTED, true), new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 8);
        Button action = button(isFutureProgram(item) ? item.optString("bookLabel", "رزرو برنامه") : item.optString("viewLabel", "مشاهده برنامه"), false);
        action.setOnClickListener(v -> {
            trackAction("program_open", metadata("program", id));
            activeProgramDetail = item;
            renderScreen("program-detail");
        });
        card.addView(action, new LinearLayout.LayoutParams(-1, dp(42)));
        return card;
    }

    private View programDetail(JSONObject item) throws Exception {
        LinearLayout card = panel(SURFACE);
        card.addView(text(item.optString("adTitle", item.getString("title")), 18, BLACK, true), new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 7);
        card.addView(text(item.optString("advertisement", item.optString("description", "")), 14, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 9);
        card.addView(text(item.getString("dateLabel") + " · " + item.optString("statusLabel", ""), 13, isFutureProgram(item) ? ACCENT : MUTED, true), new LinearLayout.LayoutParams(-1, -2));

        JSONArray details = item.optJSONArray("details");
        if (details != null && details.length() > 0) {
            addSpace(card, 10);
            for (int i = 0; i < details.length(); i++) {
                card.addView(text("• " + details.getString(i), 13, BLACK, false), new LinearLayout.LayoutParams(-1, -2));
                if (i < details.length() - 1) addSpace(card, 4);
            }
        }

        if (isFutureProgram(item)) {
            addSpace(card, 12);
            Button book = button(item.optString("bookLabel", "رزرو برنامه"), true);
            book.setOnClickListener(v -> {
                trackAction("program_book", metadata("program", item.optString("id", "")));
                submitProgramBooking(item);
            });
            card.addView(book, new LinearLayout.LayoutParams(-1, dp(44)));
        }
        return card;
    }

    private void submitProgramBooking(JSONObject item) {
        if (isMissingCustomerIdentity()) {
            Toast.makeText(this, "نام و شماره تماس را در حساب وارد کنید", Toast.LENGTH_SHORT).show();
            openScreen("account");
            return;
        }

        String url = remoteUrl("programBookingsUrl", "/program-bookings");
        if (url.isEmpty()) {
            Toast.makeText(this, "درخواست رزرو برنامه ثبت شد", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("program", item.optString("id", ""));
            payload.put("customer_name", customerNameOrFallback());
            payload.put("customer_phone", customerPhone);
            payload.put("customer_email", customerEmail);
            payload.put("attendees", 1);

            new Thread(() -> {
                try {
                    postJson(url, payload.toString());
                    runOnUiThread(() -> {
                        Toast.makeText(this, "درخواست رزرو برنامه ثبت شد", Toast.LENGTH_SHORT).show();
                        refreshCustomerScreens();
                    });
                } catch (Exception e) {
                    trackError("program_book", e);
                    runOnUiThread(() -> Toast.makeText(this, "خطا در رزرو برنامه", Toast.LENGTH_SHORT).show());
                }
            }).start();
        } catch (Exception e) {
            trackError("program_book_payload", e);
            Toast.makeText(this, "خطا در رزرو برنامه", Toast.LENGTH_SHORT).show();
        }
    }

    private View gallery(JSONArray photos) throws Exception {
        LinearLayout gallery = new LinearLayout(this);
        gallery.setOrientation(LinearLayout.VERTICAL);

        JSONObject featured = photos.getJSONObject(0);
        gallery.addView(galleryPhoto(featured, true), new LinearLayout.LayoutParams(-1, dp(DETAIL_MEDIA_HEIGHT_DP)));

        if (photos.length() == 1) {
            return gallery;
        }

        addSpace(gallery, 10);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        int index = 1;
        while (index < photos.length()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 2 && index < photos.length(); col++, index++) {
                JSONObject photo = photos.getJSONObject(index);
                View tile = galleryPhoto(photo, false);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(GALLERY_TILE_HEIGHT_DP), 1);
                params.setMargins(dp(4), dp(4), dp(4), dp(4));
                row.addView(tile, params);
            }
            grid.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        gallery.addView(grid);
        return gallery;
    }

    private View galleryPhoto(JSONObject photo, boolean featured) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.BOTTOM | Gravity.START);
        tile.setPadding(dp(12), dp(12), dp(12), dp(12));
        tile.setBackground(rounded(photo.optString("thumbnailColor", "#101114")));

        TextView marker = text(photo.optString("thumbnailText", "PHOTO"), featured ? 28 : 18, WHITE, true);
        marker.setGravity(Gravity.START);
        tile.addView(marker, new LinearLayout.LayoutParams(-1, 0, 1));

        String captionValue = photo.optString("caption", "");
        if (!captionValue.isEmpty()) {
            TextView caption = text(captionValue, featured ? 15 : 12, WHITE, true);
            caption.setMaxLines(featured ? 2 : 3);
            caption.setEllipsize(TextUtils.TruncateAt.END);
            tile.addView(caption, new LinearLayout.LayoutParams(-1, -2));
        }
        return tile;
    }

    private void openProductDetail(JSONObject item) {
        activeProductDetail = item;
        productReturnScreen = "product-detail".equals(currentScreen) ? "shop" : currentScreen;
        activeProductGalleryIndex = 0;
        trackAction("product_open", metadata("product", item.optString("id", item.optString("title", ""))));
        renderScreen("product-detail");
    }

    private String productReturnScreenLabel() {
        return "home".equals(productReturnScreen) ? "بازگشت به خانه" : "بازگشت به فروشگاه";
    }

    private View productDetailGallery(JSONObject item) throws Exception {
        LinearLayout gallery = new LinearLayout(this);
        gallery.setOrientation(LinearLayout.VERTICAL);
        JSONArray photos = productGalleryItems(item);
        if (activeProductGalleryIndex < 0 || activeProductGalleryIndex >= photos.length()) {
            activeProductGalleryIndex = 0;
        }

        JSONObject featured = photos.getJSONObject(activeProductGalleryIndex);
        gallery.addView(thumbnail(featured, dp(DETAIL_MEDIA_HEIGHT_DP)), new LinearLayout.LayoutParams(-1, dp(DETAIL_MEDIA_HEIGHT_DP)));
        String caption = featured.optString("caption", "");
        if (!caption.isEmpty()) {
            addSpace(gallery, 8);
            gallery.addView(text(caption, 13, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        }

        if (photos.length() <= 1) {
            return gallery;
        }

        addSpace(gallery, 10);
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < photos.length(); i++) {
            JSONObject photo = photos.getJSONObject(i);
            int index = i;
            boolean selected = index == activeProductGalleryIndex;
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.START);
            tile.setPadding(dp(8), dp(8), dp(8), dp(8));
            tile.setBackground(rounded(selected ? RED_TINT : SURFACE, 10, selected ? RED : BORDER, selected ? 2 : 1));
            tile.setFocusable(true);
            tile.setContentDescription(photo.optString("caption", "تصویر محصول") + (selected ? "، فعال" : ""));
            attachPressAnimation(tile);
            tile.setOnClickListener(v -> {
                activeProductGalleryIndex = index;
                trackAction("product_gallery_select", metadata("index", String.valueOf(index)));
                renderScreen("product-detail", false);
            });
            tile.addView(thumbnail(photo, dp(THUMBNAIL_MEDIA_SIZE_DP)), new LinearLayout.LayoutParams(-1, dp(THUMBNAIL_MEDIA_SIZE_DP)));
            String tileCaption = photo.optString("caption", "");
            if (!tileCaption.isEmpty()) {
                addSpace(tile, 6);
                TextView captionView = text(tileCaption, 11, selected ? RED : MUTED, selected);
                captionView.setMaxLines(2);
                captionView.setEllipsize(TextUtils.TruncateAt.END);
                tile.addView(captionView, new LinearLayout.LayoutParams(-1, -2));
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(138), dp(142));
            params.setMargins(dp(5), dp(2), dp(5), dp(2));
            row.addView(tile, params);
        }
        scroll.addView(row);
        gallery.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        return gallery;
    }

    private JSONArray productGalleryItems(JSONObject item) throws Exception {
        JSONArray provided = item.optJSONArray("gallery");
        if (provided != null && provided.length() > 0) {
            return provided;
        }

        JSONArray gallery = new JSONArray();
        String textValue = item.optString("thumbnailText", "BIKE");
        String colorValue = item.optString("thumbnailColor", "#D71920");
        String imageUrl = item.optString("imageUrl", "");
        gallery.put(productGalleryItem(textValue, colorValue, imageUrl, "نمای اصلی محصول"));
        gallery.put(productGalleryItem(productCategoryVisual(item.optString("category", "")), "#101114", "", productCategoryGalleryCaption(item.optString("category", ""))));
        gallery.put(productGalleryItem("FIT", "#1B4D3E", "", "انتخاب سایز، تست و تنظیم قبل از تحویل"));
        return gallery;
    }

    private JSONObject productGalleryItem(String textValue, String colorValue, String imageUrl, String caption) throws Exception {
        JSONObject photo = new JSONObject();
        photo.put("thumbnailText", textValue);
        photo.put("thumbnailColor", colorValue);
        photo.put("imageUrl", imageUrl == null ? "" : imageUrl);
        photo.put("caption", caption);
        return photo;
    }

    private View productBuyingPanel(JSONObject item) throws Exception {
        LinearLayout panel = panel(SURFACE);
        panel.addView(text(item.getString("title"), 24, BLACK, true), new LinearLayout.LayoutParams(-1, -2));
        String subtitle = item.optString("subtitle", "");
        if (!subtitle.isEmpty()) {
            addSpace(panel, 6);
            panel.addView(text(subtitle, 14, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        }

        addSpace(panel, 12);
        LinearLayout priceRow = new LinearLayout(this);
        priceRow.setOrientation(LinearLayout.HORIZONTAL);
        priceRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView price = text(item.optString("price", item.has("priceValue") ? formatToman(item.optInt("priceValue", 0)) : "استعلام قیمت"), 22, RED, true);
        priceRow.addView(price, new LinearLayout.LayoutParams(0, -2, 1));
        TextView stock = productAvailabilityPill(item);
        priceRow.addView(stock, new LinearLayout.LayoutParams(-2, dp(32)));
        panel.addView(priceRow, new LinearLayout.LayoutParams(-1, -2));

        String description = item.optString("description", "");
        if (!description.isEmpty()) {
            addSpace(panel, 12);
            TextView body = text(description, 14, MUTED, false);
            body.setMaxLines(6);
            panel.addView(body, new LinearLayout.LayoutParams(-1, -2));
        }

        addSpace(panel, 14);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        boolean unavailable = isProductUnavailable(item);
        Button add = button(unavailable ? "ناموجود" : (item.has("priceValue") ? "افزودن به سبد" : "افزودن / رزرو"), true);
        if (unavailable) {
            disableProductAction(add);
        } else {
            add.setOnClickListener(v -> {
                trackAction("product_detail_add_to_cart", metadata("product", item.optString("id", item.optString("title", ""))));
                addProductToCart(item);
            });
        }
        Button consult = button("مشاوره خرید", false);
        consult.setOnClickListener(v -> {
            trackAction("product_detail_consult", metadata("product", item.optString("id", item.optString("title", ""))));
            openScreen("services");
        });
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        addParams.setMargins(dp(4), dp(0), dp(0), dp(0));
        LinearLayout.LayoutParams consultParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        consultParams.setMargins(dp(0), dp(0), dp(4), dp(0));
        actions.addView(add, addParams);
        actions.addView(consult, consultParams);
        panel.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        return panel;
    }

    private View productSpecsPanel(JSONObject item) throws Exception {
        LinearLayout panel = panel(SURFACE);
        panel.addView(text("مشخصات سریع", 18, BLACK, true), new LinearLayout.LayoutParams(-1, -2));
        addSpace(panel, 10);

        JSONArray facts = new JSONArray();
        facts.put(productFact("دسته‌بندی", productCategoryLabel(item)));
        facts.put(productFact("وضعیت", productAvailabilityLabel(item)));
        facts.put(productFact("تحویل", item.optString("deliveryLabel", "تنظیم و تست قبل تحویل")));
        facts.put(productFact("پشتیبانی", item.optString("supportLabel", "مشاوره سایز و مسیر")));

        for (int i = 0; i < facts.length(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 2 && i + col < facts.length(); col++) {
                View cell = productFactCell(facts.getJSONObject(i + col));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(82), 1);
                params.setMargins(dp(4), dp(4), dp(4), dp(4));
                row.addView(cell, params);
            }
            panel.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        return panel;
    }

    private JSONObject productFact(String label, String value) throws Exception {
        JSONObject fact = new JSONObject();
        fact.put("label", label);
        fact.put("value", value);
        return fact;
    }

    private View productFactCell(JSONObject fact) throws Exception {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        cell.setPadding(dp(12), dp(8), dp(12), dp(8));
        cell.setBackground(rounded(SURFACE_ALT, 10, BORDER, 1));
        cell.addView(text(fact.getString("label"), 12, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        addSpace(cell, 4);
        TextView value = text(fact.getString("value"), 14, BLACK, true);
        value.setMaxLines(2);
        value.setEllipsize(TextUtils.TruncateAt.END);
        cell.addView(value, new LinearLayout.LayoutParams(-1, -2));
        return cell;
    }

    private View productSupportPanel() {
        LinearLayout panel = panel(SURFACE);
        panel.addView(text("قبل از تحویل", 18, BLACK, true), new LinearLayout.LayoutParams(-1, -2));
        addSpace(panel, 8);
        panel.addView(text("✓ تست ترمز، دنده و فرمان قبل از خروج از فروشگاه", 13, BLACK, false), new LinearLayout.LayoutParams(-1, -2));
        addSpace(panel, 6);
        panel.addView(text("✓ انتخاب سایز و تنظیم اولیه بر اساس مسیر استفاده", 13, BLACK, false), new LinearLayout.LayoutParams(-1, -2));
        addSpace(panel, 6);
        panel.addView(text("✓ امکان هماهنگی سرویس اول و پیگیری از حساب کاربری", 13, BLACK, false), new LinearLayout.LayoutParams(-1, -2));
        return panel;
    }

    private String productCategoryLabel(String category) {
        if ("bikes".equals(category)) return "دوچرخه";
        if ("parts".equals(category)) return "قطعات";
        if ("accessories".equals(category)) return "لوازم جانبی";
        return category == null || category.trim().isEmpty() ? "محصول" : category;
    }

    private String productCategoryLabel(JSONObject item) {
        String label = item.optString("categoryLabel", "").trim();
        return label.isEmpty() ? productCategoryLabel(item.optString("category", "")) : label;
    }

    private String productCategoryVisual(String category) {
        if ("parts".equals(category)) return "PART";
        if ("accessories".equals(category)) return "GEAR";
        return "BIKE";
    }

    private String productCategoryGalleryCaption(String category) {
        if ("parts".equals(category)) return "جزئیات فنی و سازگاری قطعه";
        if ("accessories".equals(category)) return "نحوه استفاده و کیفیت ساخت";
        return "فریم، قطعات و حالت رکاب‌زنی";
    }

    private String productAvailabilityLabel(JSONObject item) {
        String stockLabel = item.optString("stockLabel", "");
        if (!stockLabel.isEmpty()) return stockLabel;

        String availability = item.optString("availability", "");
        switch (availability) {
            case "in_stock":
                return "موجود";
            case "low_stock":
                return "موجودی محدود";
            case "orderable":
                return "قابل سفارش";
            case "out_of_stock":
                return "ناموجود";
            default:
                return "استعلام موجودی";
        }
    }

    private boolean isProductUnavailable(JSONObject item) {
        return "out_of_stock".equals(item.optString("availability", ""));
    }

    private TextView productAvailabilityPill(JSONObject item) {
        String availability = item.optString("availability", "");
        if ("in_stock".equals(availability)) {
            return pillText(productAvailabilityLabel(item), 12, SUCCESS, SUCCESS_SOFT, SUCCESS);
        }
        if ("low_stock".equals(availability)) {
            return pillText(productAvailabilityLabel(item), 12, WARNING, WARNING_SOFT, WARNING);
        }
        if ("orderable".equals(availability)) {
            return pillText(productAvailabilityLabel(item), 12, RED, RED_TINT, RED);
        }
        return pillText(productAvailabilityLabel(item), 12, MUTED, NEUTRAL_SOFT, BORDER);
    }

    private void disableProductAction(Button button) {
        button.setEnabled(false);
        button.setTextColor(MUTED);
        button.setBackground(rounded(NEUTRAL_SOFT, 10, BORDER, 1));
        button.setContentDescription("ناموجود");
    }

    private boolean isFutureProgram(JSONObject item) {
        return "future".equals(item.optString("programState", ""));
    }

    private boolean isFinishedProgram(JSONObject item) {
        return "finished".equals(item.optString("programState", ""));
    }

    private View messageCenter(JSONObject section) throws Exception {
        JSONObject data = sectionData(section);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        String key = currentScreen + ":" + section.optString("id", data.optString("title", "messages"));

        wrap.addView(sectionTitle(data.getString("title")));
        wrap.addView(infoPanel(data.getJSONObject("summary")));
        addSpace(wrap, 12);

        JSONArray departments = data.getJSONArray("departments");
        String selected = selectedMessageDepartments.containsKey(key)
                ? selectedMessageDepartments.get(key)
                : data.optString("defaultDepartment", departments.getJSONObject(0).getString("id"));
        JSONObject department = departments.getJSONObject(0);
        for (int i = 0; i < departments.length(); i++) {
            JSONObject candidate = departments.getJSONObject(i);
            if (candidate.getString("id").equals(selected)) {
                department = candidate;
                break;
            }
        }

        wrap.addView(messageInboxOverview(departments, key, selected));
        addSpace(wrap, 12);
        wrap.addView(messageThread(department));
        addSpace(wrap, 12);
        wrap.addView(messageComposer(department, departments, key, selected));
        return wrap;
    }

    private View messageInboxOverview(JSONArray departments, String key, String selected) throws Exception {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.addView(sectionTitle("صندوق پیام‌ها"));
        for (int i = 0; i < departments.length(); i++) {
            JSONObject department = departments.getJSONObject(i);
            String id = department.getString("id");
            LinearLayout card = panel(id.equals(selected) ? SURFACE : WHITE);
            card.setBackground(interactiveBackground(id.equals(selected) ? SURFACE : WHITE, 8, BORDER, 1));
            card.setFocusable(true);
            attachPressAnimation(card);
            card.setOnClickListener(v -> {
                trackAction("message_department_open", metadata("department", id));
                selectedMessageDepartments.put(key, id);
                renderScreen(currentScreen);
            });
            card.addView(text(department.getString("title"), 16, BLACK, true), new LinearLayout.LayoutParams(-1, -2));
            addSpace(card, 4);
            JSONArray messages = department.getJSONArray("messages");
            String latest = messages.length() > 0 ? messages.getJSONObject(0).optString("text", "") : "";
            TextView preview = text(latest, 12, MUTED, false);
            preview.setMaxLines(2);
            preview.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(preview, new LinearLayout.LayoutParams(-1, -2));
            String unread = department.optString("unreadLabel", "");
            if (!unread.isEmpty()) {
                addSpace(card, 6);
                card.addView(text(unread, 12, ACCENT, true), new LinearLayout.LayoutParams(-1, -2));
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(dp(0), dp(4), dp(0), dp(6));
            list.addView(card, params);
        }
        return list;
    }

    private View cartSummary(JSONObject data) throws Exception {
        refreshCartForScreen();

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(sectionTitle(data.getString("title")));

        if (cartItems.isEmpty()) {
            LinearLayout empty = panel(WHITE);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(22), dp(16), dp(22));
            empty.addView(text("سبد خرید خالی است", 18, BLACK, true), new LinearLayout.LayoutParams(-1, -2));
            addSpace(empty, 6);
            empty.addView(text(data.optString("emptyStateText", "برای ثبت سفارش، ابتدا محصولی به سبد اضافه کنید."), 13, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
            addSpace(empty, 12);
            Button shop = button("رفتن به فروشگاه", true);
            shop.setOnClickListener(v -> {
                trackAction("cart_empty_shop");
                openScreen("shop");
            });
            empty.addView(shop, new LinearLayout.LayoutParams(-1, dp(46)));
            wrap.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return wrap;
        }

        for (JSONObject item : cartItems.values()) {
            LinearLayout card = cartLineCard(item);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(dp(0), dp(5), dp(0), dp(7));
            wrap.addView(card, params);
        }

        LinearLayout customer = panel(WHITE);
        customer.addView(text("اطلاعات تحویل", 18, BLACK, true), new LinearLayout.LayoutParams(-1, -2));
        addSpace(customer, 8);
        EditText name = addTextField(customer, "نام", customerName, false, 1);
        addSpace(customer, 8);
        EditText phone = addTextField(customer, "شماره تماس", customerPhone, false, 1);
        addSpace(customer, 8);
        EditText email = addTextField(customer, "ایمیل", customerEmail, false, 1);
        addSpace(customer, 8);
        EditText address = addTextField(customer, "آدرس تحویل", customerAddress, false, 2);
        addSpace(customer, 8);
        Spinner fulfillment = addChoiceField(customer, "روش دریافت", new String[]{"تحویل حضوری", "ارسال"});
        addSpace(customer, 8);
        Spinner payment = addChoiceField(customer, "روش پرداخت", new String[]{"پرداخت در فروشگاه", "پرداخت هنگام تحویل", "هماهنگی کارت‌به‌کارت"});
        LinearLayout.LayoutParams customerParams = new LinearLayout.LayoutParams(-1, -2);
        customerParams.setMargins(dp(0), dp(5), dp(0), dp(7));
        wrap.addView(customer, customerParams);

        LinearLayout total = panel(SURFACE);
        LinearLayout totalRow = new LinearLayout(this);
        totalRow.setOrientation(LinearLayout.HORIZONTAL);
        totalRow.setGravity(Gravity.CENTER_VERTICAL);
        totalRow.addView(text(data.optString("totalLabel", "جمع کل"), 13, MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
        TextView totalValue = text(formatToman(cartSubtotal()), 20, BLACK, true);
        totalValue.setGravity(Gravity.END);
        totalRow.addView(totalValue, new LinearLayout.LayoutParams(0, -2, 1));
        total.addView(totalRow, new LinearLayout.LayoutParams(-1, -2));
        addSpace(total, 10);
        Button checkout = button(data.optString("checkoutLabel", "ثبت سفارش"), true);
        checkout.setOnClickListener(v -> {
            trackAction("checkout");
            saveCustomerInputs(name, phone, email, address);
            submitOrder(fulfillment.getSelectedItemPosition() == 1 ? "delivery" : "pickup", paymentMethodValue(payment.getSelectedItemPosition()));
        });
        total.addView(checkout, new LinearLayout.LayoutParams(-1, dp(46)));
        wrap.addView(total, new LinearLayout.LayoutParams(-1, -2));
        return wrap;
    }

    private LinearLayout cartLineCard(JSONObject item) {
        LinearLayout card = panel(WHITE);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        row.addView(thumbnail(item, dp(THUMBNAIL_MEDIA_SIZE_DP)), new LinearLayout.LayoutParams(dp(THUMBNAIL_MEDIA_SIZE_DP), dp(THUMBNAIL_MEDIA_SIZE_DP)));

        LinearLayout detail = new LinearLayout(this);
        detail.setOrientation(LinearLayout.VERTICAL);
        detail.setGravity(Gravity.START);
        detail.addView(text(item.optString("title", "محصول"), 16, BLACK, true), new LinearLayout.LayoutParams(-1, -2));
        String subtitle = item.optString("subtitle", item.optString("stockLabel", ""));
        if (!subtitle.isEmpty()) {
            addSpace(detail, 4);
            detail.addView(text(subtitle, 12, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        }
        addSpace(detail, 8);
        int quantity = Math.max(1, item.optInt("quantity", 1));
        detail.addView(text("تعداد: " + persianDigits(quantity) + " · " + formatToman(cartLineTotal(item)), 13, ACCENT, true), new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(0, -2, 1);
        detailParams.setMargins(dp(0), dp(0), dp(12), dp(0));
        row.addView(detail, detailParams);
        card.addView(row, new LinearLayout.LayoutParams(-1, -2));

        addSpace(card, 12);
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        Button remove = button("حذف", false);
        Button minus = button("-", false);
        Button plus = button("+", false);
        remove.setOnClickListener(v -> updateCartItem(item, 0));
        minus.setOnClickListener(v -> updateCartItem(item, quantity - 1));
        plus.setOnClickListener(v -> updateCartItem(item, quantity + 1));
        controls.addView(remove, new LinearLayout.LayoutParams(0, dp(42), 1));
        controls.addView(minus, new LinearLayout.LayoutParams(0, dp(42), 1));
        controls.addView(plus, new LinearLayout.LayoutParams(0, dp(42), 1));
        card.addView(controls, new LinearLayout.LayoutParams(-1, -2));

        return card;
    }

    private void updateCartItem(JSONObject item, int quantity) {
        String cartItemId = item.optString("cartItemId", "").trim();
        String productId = item.optString("id", "").trim();

        if (quantity < 1) {
            trackAction("cart_remove", metadata("product", productId));
        } else {
            trackAction("cart_quantity", metadata("product", productId));
        }

        if (cartItemId.isEmpty()) {
            if (quantity < 1) {
                cartItems.remove(productId);
            } else {
                try {
                    item.put("quantity", quantity);
                    item.remove("lineTotal");
                    cartItems.put(productId, item);
                } catch (Exception ignored) {
                }
            }
            cartCount = cartQuantity();
            updateCartButton();
            renderScreen("cart", false);
            return;
        }

        String url = cartItemUrl(cartItemId);
        if (url.isEmpty()) {
            Toast.makeText(this, "خطا در به‌روزرسانی سبد", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                JSONObject response;
                if (quantity < 1) {
                    response = new JSONObject(deleteJson(url, devicePayload().toString()));
                } else {
                    JSONObject payload = devicePayload();
                    payload.put("quantity", quantity);
                    response = new JSONObject(patchJson(url, payload.toString()));
                }

                JSONObject data = response.optJSONObject("data");
                runOnUiThread(() -> {
                    if (data != null) {
                        applyCartPayload(data);
                    }
                    Toast.makeText(this, quantity < 1 ? "از سبد حذف شد" : "سبد به‌روزرسانی شد", Toast.LENGTH_SHORT).show();
                    if ("cart".equals(currentScreen)) {
                        renderScreen("cart", false);
                    }
                });
            } catch (Exception e) {
                trackError(quantity < 1 ? "cart_remove" : "cart_update", e);
                runOnUiThread(() -> Toast.makeText(this, "خطا در به‌روزرسانی سبد", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private JSONObject devicePayload() throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("device_id", telemetryDeviceId);
        return payload;
    }

    private String cartItemUrl(String cartItemId) {
        String base = remoteUrl("cartItemsUrl", "/cart/items");
        if (base.isEmpty()) {
            return "";
        }

        return base.endsWith("/") ? base + cartItemId : base + "/" + cartItemId;
    }

    private void submitOrder(String fulfillmentMethod, String paymentMethod) {
        if (cartItems.isEmpty()) {
            Toast.makeText(this, "ابتدا محصولی به سبد اضافه کنید", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isMissingCustomerIdentity()) {
            Toast.makeText(this, "نام و شماره تماس را وارد کنید", Toast.LENGTH_SHORT).show();
            return;
        }

        if ("delivery".equals(fulfillmentMethod) && (customerAddress == null || customerAddress.trim().isEmpty())) {
            Toast.makeText(this, "برای ارسال، آدرس تحویل را وارد کنید", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = remoteUrl("ordersUrl", "/orders");
        if (url.isEmpty()) {
            cartItems.clear();
            cartCount = 0;
            updateCartButton();
            Toast.makeText(this, "سفارش ثبت شد", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("customer_name", customerNameOrFallback());
            payload.put("customer_phone", customerPhone);
            payload.put("customer_email", customerEmail);
            payload.put("delivery_address", customerAddress);
            payload.put("fulfillment_method", fulfillmentMethod);
            payload.put("payment_method", paymentMethod);
            payload.put("device_id", telemetryDeviceId);
            JSONArray items = new JSONArray();
            for (String key : cartItems.keySet()) {
                JSONObject product = cartItems.get(key);
                if (product == null) {
                    continue;
                }
                JSONObject orderItem = new JSONObject();
                int quantity = product.optInt("quantity", 1);
                orderItem.put("product_id", product.optString("id", key));
                orderItem.put("title", product.optString("title", key));
                orderItem.put("quantity", quantity);
                orderItem.put("unit_price", product.optInt("priceValue", 0));
                items.put(orderItem);
            }
            payload.put("items", items);

            new Thread(() -> {
                try {
                    postJson(url, payload.toString());
                    runOnUiThread(() -> {
                        cartItems.clear();
                        cartCount = 0;
                        updateCartButton();
                        Toast.makeText(this, "سفارش ثبت شد", Toast.LENGTH_SHORT).show();
                        refreshCustomerScreens();
                        refreshMobileState();
                    });
                } catch (Exception e) {
                    trackError("checkout", e);
                    runOnUiThread(() -> Toast.makeText(this, "خطا در ثبت سفارش", Toast.LENGTH_SHORT).show());
                }
            }).start();
        } catch (Exception e) {
            trackError("checkout_payload", e);
            Toast.makeText(this, "خطا در ثبت سفارش", Toast.LENGTH_SHORT).show();
        }
    }

    private View serviceBookingForm(JSONObject data) throws Exception {
        LinearLayout card = panel(WHITE);
        Spinner service = addDropdownField(card, data.getString("serviceLabel"), data.getJSONArray("services"));
        addSpace(card, 10);
        Spinner bike = addDropdownField(card, data.getString("bikeLabel"), data.getJSONArray("bikes"));
        addSpace(card, 10);
        Spinner time = addDropdownField(card, data.getString("timeLabel"), data.getJSONArray("timeSlots"));
        addSpace(card, 10);

        EditText problem = new EditText(this);
        problem.setHint(data.optString("problemPlaceholder", "توضیح مشکل"));
        problem.setTextSize(14);
        problem.setTextColor(BLACK);
        problem.setHintTextColor(MUTED);
        problem.setTypeface(bodyTypeface);
        problem.setGravity(Gravity.START);
        problem.setMinLines(3);
        problem.setBackground(rounded(SURFACE, 8, BORDER, 1));
        problem.setPadding(dp(12), dp(8), dp(12), dp(8));
        card.addView(problem, new LinearLayout.LayoutParams(-1, dp(104)));
        addSpace(card, 10);

        Button submit = button(data.optString("submitLabel", "ثبت درخواست سرویس"), true);
        submit.setOnClickListener(v -> {
            trackAction("service_booking_submit");
            submitServiceBooking(
                    stringValue(service.getSelectedItem()),
                    stringValue(bike.getSelectedItem()),
                    stringValue(time.getSelectedItem()),
                    problem.getText().toString().trim(),
                    problem
            );
        });
        card.addView(submit, new LinearLayout.LayoutParams(-1, dp(46)));
        return card;
    }

    private Spinner addDropdownField(LinearLayout parent, String label, JSONArray options) throws Exception {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.START);
        box.addView(text(label, 13, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        addSpace(box, 4);

        List<String> labels = new ArrayList<>();
        for (int i = 0; i < options.length(); i++) {
            labels.add(options.getString(i));
        }
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        box.addView(spinner, new LinearLayout.LayoutParams(-1, dp(48)));
        parent.addView(box, new LinearLayout.LayoutParams(-1, -2));
        return spinner;
    }

    private View statusTrackers(JSONObject section) throws Exception {
        JSONObject data = sectionData(section);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        JSONArray items = data.getJSONArray("items");
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String key = currentScreen + ":" + section.optString("id", data.optString("title", "status")) + ":" + item.optString("id", i + "");
            View card = ongoingPurchaseCard(item, key);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(dp(0), dp(5), dp(0), dp(7));
            list.addView(card, params);
        }
        return list;
    }

    private void submitServiceBooking(String service, String bike, String time, String problem, EditText problemInput) {
        if (isMissingCustomerIdentity()) {
            Toast.makeText(this, "نام و شماره تماس را در حساب وارد کنید", Toast.LENGTH_SHORT).show();
            openScreen("account");
            return;
        }

        String url = remoteUrl("serviceBookingsUrl", "/service-bookings");
        if (url.isEmpty()) {
            Toast.makeText(this, "درخواست سرویس ثبت شد", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("customer_name", customerNameOrFallback());
            payload.put("customer_phone", customerPhone);
            payload.put("customer_email", customerEmail);
            payload.put("service_type", service);
            payload.put("bike_label", bike);
            payload.put("preferred_time", time);
            payload.put("problem_description", problem);

            new Thread(() -> {
                try {
                    postJson(url, payload.toString());
                    runOnUiThread(() -> {
                        problemInput.setText("");
                        Toast.makeText(this, "درخواست سرویس ثبت شد", Toast.LENGTH_SHORT).show();
                        refreshCustomerScreens();
                        refreshMobileState();
                    });
                } catch (Exception e) {
                    trackError("service_booking_submit", e);
                    runOnUiThread(() -> Toast.makeText(this, "خطا در ثبت درخواست سرویس", Toast.LENGTH_SHORT).show());
                }
            }).start();
        } catch (Exception e) {
            trackError("service_booking_payload", e);
            Toast.makeText(this, "خطا در ثبت درخواست سرویس", Toast.LENGTH_SHORT).show();
        }
    }

    private View bikeProfiles(JSONArray items) throws Exception {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            LinearLayout card = panel(WHITE);
            card.addView(text(item.getString("title"), 18, BLACK, true), new LinearLayout.LayoutParams(-1, -2));
            addSpace(card, 6);
            card.addView(text(item.getString("subtitle"), 13, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
            addSpace(card, 8);
            JSONArray fields = item.getJSONArray("fields");
            for (int j = 0; j < fields.length(); j++) {
                JSONObject field = fields.getJSONObject(j);
                card.addView(text(field.getString("label") + ": " + field.getString("value"), 13, BLACK, false), new LinearLayout.LayoutParams(-1, -2));
                if (j < fields.length() - 1) addSpace(card, 4);
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(dp(0), dp(5), dp(0), dp(7));
            list.addView(card, params);
        }
        return list;
    }

    private View businessInfo(JSONArray items) throws Exception {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            View card = accountCard(item);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(dp(0), dp(5), dp(0), dp(7));
            list.addView(card, params);
        }
        return list;
    }

    private View departmentDropdown(JSONArray departments, String key, String selected) throws Exception {
        LinearLayout box = panel(SURFACE);
        box.addView(text("دسته‌بندی پیام", 13, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        addSpace(box, 6);

        List<String> labels = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        int selectedIndex = 0;
        for (int i = 0; i < departments.length(); i++) {
            JSONObject department = departments.getJSONObject(i);
            String id = department.getString("id");
            String unread = department.optString("unreadLabel", "");
            ids.add(id);
            labels.add(unread.isEmpty() ? department.getString("title") : department.getString("title") + " - " + unread);
            if (id.equals(selected)) {
                selectedIndex = i;
            }
        }

        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selectedIndex, false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String next = ids.get(position);
                if (!next.equals(selectedMessageDepartments.get(key)) && !next.equals(selected)) {
                    selectedMessageDepartments.put(key, next);
                    renderScreen(currentScreen);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        box.addView(spinner, new LinearLayout.LayoutParams(-1, dp(48)));
        return box;
    }

    private View messageThread(JSONObject department) throws Exception {
        LinearLayout card = panel(SURFACE);
        card.addView(text(department.getString("threadTitle"), 18, BLACK, true), new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 8);
        JSONArray messages = department.getJSONArray("messages");
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.getJSONObject(i);
            LinearLayout bubble = panel("client".equals(message.optString("sender", "")) ? WHITE : RED_TINT);
            bubble.addView(text(message.getString("label"), 12, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
            addSpace(bubble, 3);
            bubble.addView(text(message.getString("text"), 14, BLACK, false), new LinearLayout.LayoutParams(-1, -2));
            addSpace(bubble, 3);
            bubble.addView(text(message.optString("time", ""), 11, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(dp(0), dp(4), dp(0), dp(6));
            card.addView(bubble, params);
        }
        return card;
    }

    private View messageComposer(JSONObject department, JSONArray departments, String key, String selected) throws Exception {
        LinearLayout card = panel(WHITE);
        card.addView(text(department.optString("composerTitle", "ارسال پیام"), 17, BLACK, true), new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 8);
        card.addView(departmentDropdown(departments, key, selected), new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 10);

        EditText input = new EditText(this);
        input.setTextSize(14);
        input.setTextColor(BLACK);
        input.setHintTextColor(MUTED);
        input.setTypeface(bodyTypeface);
        input.setHint(department.optString("placeholder", "پیام خود را بنویسید"));
        input.setGravity(Gravity.START);
        input.setMinLines(3);
        input.setBackground(rounded(SURFACE, 8, BORDER, 1));
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        card.addView(input, new LinearLayout.LayoutParams(-1, dp(108)));
        addSpace(card, 10);

        Button send = button(department.optString("sendLabel", "ارسال پیام"), true);
        send.setOnClickListener(v -> {
            trackAction("message_send", metadata("department", department.optString("id", "")));
            submitMessage(department, input);
        });
        card.addView(send, new LinearLayout.LayoutParams(-1, dp(44)));
        return card;
    }

    private void submitMessage(JSONObject department, EditText input) {
        String message = input.getText().toString().trim();
        if (message.isEmpty()) {
            Toast.makeText(this, "پیام را بنویسید", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isMissingCustomerIdentity()) {
            Toast.makeText(this, "نام و شماره تماس را در حساب وارد کنید", Toast.LENGTH_SHORT).show();
            openScreen("account");
            return;
        }

        String url = remoteUrl("messagesUrl", "/messages");
        if (url.isEmpty()) {
            input.setText("");
            Toast.makeText(this, "پیام برای " + department.optString("title", "واحد پشتیبانی") + " ثبت شد", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("department", department.optString("id", ""));
            payload.put("customer_name", customerNameOrFallback());
            payload.put("customer_phone", customerPhone);
            payload.put("customer_email", customerEmail);
            payload.put("label", customerNameOrFallback());
            payload.put("text", message);

            new Thread(() -> {
                try {
                    postJson(url, payload.toString());
                    runOnUiThread(() -> {
                        input.setText("");
                        Toast.makeText(this, "پیام ثبت شد", Toast.LENGTH_SHORT).show();
                        refreshCustomerScreens();
                        refreshMobileState();
                    });
                } catch (Exception e) {
                    trackError("message_send", e);
                    runOnUiThread(() -> Toast.makeText(this, "خطا در ارسال پیام", Toast.LENGTH_SHORT).show());
                }
            }).start();
        } catch (Exception e) {
            trackError("message_payload", e);
            Toast.makeText(this, "خطا در ارسال پیام", Toast.LENGTH_SHORT).show();
        }
    }

    private View productList(JSONObject section) throws Exception {
        String key = currentScreen + ":" + section.optString("id", section.optString("title", "products"));
        String selectedCategory = selectedProductCategory(section, key);
        Map<String, String> filters = currentFilterValues(section, key);
        String query = searchQueries.containsKey(key) ? searchQueries.get(key) : "";
        JSONArray categories = section.optJSONArray("categories");
        if (categories == null) {
            categories = new JSONArray();
        }
        List<JSONObject> items = filteredProducts(section.getJSONArray("items"), categories, selectedCategory, filters, query);
        int initialItems = section.optInt("initialItems", 4);
        int pageSize = section.optInt("pageSize", initialItems);
        Integer storedVisible = visibleItemCounts.get(key);
        int visible = storedVisible == null ? initialItems : storedVisible;
        visible = Math.min(visible, items.size());

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.addView(shopControlsPanel(section, key, categories, selectedCategory, filters, query, items.size()), new LinearLayout.LayoutParams(-1, -2));
        addSpace(list, 14);

        if (visible == 0) {
            list.addView(emptyProductsPanel(section, key));
        } else {
            addProductCards(list, items, visible);
        }

        if (visible < items.size()) {
            String moreLabel = String.format(Locale.US, "%s (%d/%d)", section.optString("loadMoreLabel", "نمایش بیشتر"), visible, items.size());
            Button more = button(moreLabel, false);
            int nextVisible = Math.min(visible + pageSize, items.size());
            more.setOnClickListener(v -> {
                trackAction("product_load_more", metadata("visible", String.valueOf(nextVisible)));
                visibleItemCounts.put(key, nextVisible);
                renderScreen(currentScreen);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
            params.setMargins(dp(0), dp(8), dp(0), dp(4));
            list.addView(more, params);
        }
        return list;
    }

    private String selectedProductCategory(JSONObject section, String key) {
        String selected = selectedCategories.containsKey(key)
                ? selectedCategories.get(key)
                : section.optString("defaultCategory", "");
        return "all".equals(selected) ? "" : selected;
    }

    private View shopControlsPanel(JSONObject section, String key, JSONArray categories, String selectedCategory,
                                   Map<String, String> filtersState, String query, int resultCount) throws Exception {
        LinearLayout panel = panel(SURFACE);
        panel.setPadding(dp(12), dp(12), dp(12), dp(12));
        panel.addView(compactSearchRow(section, key, query), new LinearLayout.LayoutParams(-1, -2));

        addSpace(panel, 10);
        panel.addView(shopSummary(section, categories, selectedCategory, filtersState, query, resultCount), new LinearLayout.LayoutParams(-1, -2));

        View quickFilters = quickFiltersRow(section, key, filtersState);
        if (quickFilters != null) {
            addSpace(panel, 10);
            panel.addView(quickFilters, new LinearLayout.LayoutParams(-1, -2));
        }

        if (categories.length() > 0) {
            addSpace(panel, 10);
            panel.addView(categoryControls(section, key, categories, selectedCategory), new LinearLayout.LayoutParams(-1, -2));
        }

        View advancedFilters = advancedFiltersPanel(section, key, filtersState, query);
        if (advancedFilters != null) {
            addSpace(panel, 10);
            panel.addView(advancedFilters, new LinearLayout.LayoutParams(-1, -2));
        }
        return panel;
    }

    private View compactSearchRow(JSONObject section, String key, String query) {
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);

        EditText input = new EditText(this);
        input.setText(query);
        input.setHint(section.optString("searchPlaceholder", "نام محصول، قطعه یا لوازم را بنویسید"));
        input.setTextSize(14);
        input.setTextColor(BLACK);
        input.setHintTextColor(MUTED);
        input.setTypeface(bodyTypeface);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setGravity(Gravity.START);
        input.setBackground(rounded(SURFACE_ALT, 12, BORDER, 1));
        input.setPadding(dp(12), dp(0), dp(12), dp(0));
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                applyProductSearch(key, input.getText().toString().trim());
                return true;
            }
            return false;
        });
        searchRow.addView(input, new LinearLayout.LayoutParams(0, dp(48), 1));

        Button apply = button(section.optString("searchActionLabel", "جستجو"), true);
        apply.setOnClickListener(v -> applyProductSearch(key, input.getText().toString().trim()));
        LinearLayout.LayoutParams applyParams = new LinearLayout.LayoutParams(dp(96), dp(48));
        applyParams.setMargins(dp(0), dp(0), dp(8), dp(0));
        searchRow.addView(apply, applyParams);
        return searchRow;
    }

    private View shopSummary(JSONObject section, JSONArray categories, String selectedCategory,
                             Map<String, String> filtersState, String query, int resultCount) throws Exception {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        addShopSummaryPill(row, persianDigits(resultCount) + " محصول", BLACK, SURFACE_ALT, BORDER);
        if (query != null && !query.trim().isEmpty()) {
            addShopSummaryPill(row, "جستجو: " + query, RED, RED_TINT, RED);
        }
        String categoryPath = selectedCategoryPath(categories, selectedCategory);
        if (!categoryPath.isEmpty()) {
            addShopSummaryPill(row, categoryPath, RED, RED_TINT, RED);
        }
        int activeControls = activeProductControlCount(section, filtersState, query);
        if (activeControls > 0) {
            addShopSummaryPill(row, persianDigits(activeControls) + " فیلتر فعال", RED, RED_TINT, RED);
        }
        scroll.addView(row);
        return scroll;
    }

    private void addShopSummaryPill(LinearLayout parent, String label, int textColor, int fillColor, int strokeColor) {
        TextView pill = pillText(label, 12, textColor, fillColor, strokeColor);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(32));
        params.setMargins(dp(3), dp(0), dp(3), dp(0));
        parent.addView(pill, params);
    }

    private View quickFiltersRow(JSONObject section, String key, Map<String, String> filtersState) throws Exception {
        JSONArray filters = section.optJSONArray("filters");
        if (filters == null) {
            return null;
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int added = 0;
        for (int i = 0; i < filters.length(); i++) {
            JSONObject filter = filters.getJSONObject(i);
            if (!isQuickFilter(section, filter) || "number".equals(filter.optString("type", ""))) {
                continue;
            }

            String id = filter.getString("id");
            String value = filtersState.containsKey(id) ? filtersState.get(id) : filter.optString("default", "all");
            TextView control = filterChoiceControl(filter, key, value);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1);
            params.setMargins(added == 0 ? dp(0) : dp(4), dp(0), added == 0 ? dp(4) : dp(0), dp(0));
            row.addView(control, params);
            added++;
        }
        return added == 0 ? null : row;
    }

    private TextView filterChoiceControl(JSONObject filter, String key, String selectedValue) throws Exception {
        String value = selectedValue == null ? filter.optString("default", "all") : selectedValue;
        boolean active = !isDefaultFilter(filter, value);
        String label = filter.getString("label") + ": " + filterOptionLabel(filter, value);
        TextView control = text(label, 12, active ? RED : BLACK, true);
        control.setGravity(Gravity.CENTER);
        control.setSingleLine(true);
        control.setEllipsize(TextUtils.TruncateAt.END);
        control.setPadding(dp(10), dp(0), dp(10), dp(0));
        control.setContentDescription(label);
        control.setFocusable(true);
        control.setBackground(interactiveBackground(active ? RED_TINT : SURFACE, 20, active ? RED : BORDER, 1));
        attachPressAnimation(control);
        control.setOnClickListener(v -> showProductFilterChoices(filter, key, value));
        return control;
    }

    private void showProductFilterChoices(JSONObject filter, String key, String selectedValue) {
        try {
            JSONArray options = filter.getJSONArray("options");
            List<String> labels = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            int selectedIndex = 0;
            for (int i = 0; i < options.length(); i++) {
                JSONObject option = options.getJSONObject(i);
                String id = option.getString("id");
                ids.add(id);
                labels.add(option.getString("label"));
                if (id.equals(selectedValue)) {
                    selectedIndex = i;
                }
            }
            new AlertDialog.Builder(this)
                    .setTitle(filter.getString("label"))
                    .setSingleChoiceItems(labels.toArray(new String[0]), selectedIndex, (dialog, which) -> {
                        try {
                            applyProductFilter(filter, key, ids.get(which), true);
                        } catch (Exception e) {
                            trackError("product_filter", e);
                            Toast.makeText(MainActivity.this, "خطا در اعمال فیلتر", Toast.LENGTH_SHORT).show();
                        }
                        dialog.dismiss();
                    })
                    .show();
        } catch (Exception e) {
            trackError("product_filter_menu", e);
            Toast.makeText(this, "خطا در نمایش فیلتر", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isQuickFilter(JSONObject section, JSONObject filter) {
        String id = filter.optString("id", "");
        JSONArray quickIds = section.optJSONArray("quickFilterIds");
        if (quickIds != null) {
            for (int i = 0; i < quickIds.length(); i++) {
                if (id.equals(quickIds.optString(i))) {
                    return true;
                }
            }
            return false;
        }
        return "availability".equals(id) || "sort".equals(id);
    }

    private boolean applyProductFilter(JSONObject filter, String key, String nextValue, boolean redraw) throws Exception {
        String id = filter.getString("id");
        String stateKey = key + ":" + id;
        String defaultValue = filter.optString("default", "all");
        String next = nextValue == null ? "" : nextValue.trim();
        String previous = selectedFilters.containsKey(stateKey) ? selectedFilters.get(stateKey) : defaultValue;
        if (next.equals(previous)) {
            return false;
        }

        if (isDefaultFilter(filter, next)) {
            selectedFilters.remove(stateKey);
            trackAction("product_filter_reset", metadata("filter", id));
        } else {
            selectedFilters.put(stateKey, next);
            trackAction("product_filter", metadata(id, next));
        }
        visibleItemCounts.remove(key);
        if (redraw) {
            renderScreen(currentScreen);
        }
        return true;
    }

    private View categoryControls(JSONObject section, String key, JSONArray categories, String selectedCategory) throws Exception {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text(section.optString("categoryLabel", "دسته‌بندی‌ها"), 13, MUTED, false);
        header.addView(label, new LinearLayout.LayoutParams(0, -2, 1));

        boolean expanded = Boolean.TRUE.equals(expandedCategorySections.get(key));
        int rootCount = rootCategoryCount(categories);
        String menuLabel = expanded
                ? "بستن دسته‌بندی‌ها"
                : section.optString("categoryMenuLabel", "دسته‌بندی‌ها") + " (" + persianDigits(rootCount) + ")";
        TextView menu = text(menuLabel, 12, RED, true);
        menu.setGravity(Gravity.CENTER);
        menu.setSingleLine(true);
        menu.setPadding(dp(12), dp(0), dp(12), dp(0));
        menu.setFocusable(true);
        menu.setBackground(interactiveBackground(RED_TINT, 18, RED, 1));
        attachPressAnimation(menu);
        menu.setOnClickListener(v -> {
            expandedCategorySections.put(key, !expanded);
            trackAction(expanded ? "product_categories_collapse" : "product_categories_expand");
            renderScreen(currentScreen);
        });
        header.addView(menu, new LinearLayout.LayoutParams(-2, dp(36)));
        wrap.addView(header, new LinearLayout.LayoutParams(-1, -2));

        addSpace(wrap, 6);
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.addView(categoryChoiceChip(section.optString("allCategoriesLabel", "همه محصولات"), "", selectedCategory, key), categoryChipParams());
        for (int i = 0; i < categories.length(); i++) {
            JSONObject category = categories.getJSONObject(i);
            if (!isRootCategory(category)) {
                continue;
            }
            chips.addView(categoryChoiceChip(categoryLabelWithCount(category), category.getString("id"), selectedCategory, key), categoryChipParams());
        }
        scroll.addView(chips);
        wrap.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        if (expanded) {
            addSpace(wrap, 8);
            wrap.addView(categoryPicker(categories, section, key, selectedCategory), new LinearLayout.LayoutParams(-1, -2));
        }
        return wrap;
    }

    private LinearLayout.LayoutParams categoryChipParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(40));
        params.setMargins(dp(3), dp(0), dp(3), dp(0));
        return params;
    }

    private TextView categoryChoiceChip(String label, String categoryId, String selectedCategory, String key) {
        boolean active = categoryId.equals(selectedCategory);
        TextView chip = text(label, 13, active ? WHITE : BLACK, true);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setPadding(dp(14), dp(0), dp(14), dp(0));
        chip.setContentDescription(active ? label + "، فعال" : label);
        chip.setFocusable(true);
        chip.setBackground(interactiveBackground(active ? RED : SURFACE, 20, active ? RED : BORDER, 1));
        attachPressAnimation(chip);
        chip.setOnClickListener(v -> selectProductCategory(key, categoryId, selectedCategory));
        return chip;
    }

    private View categoryPicker(JSONArray categories, JSONObject section, String key, String selectedCategory) throws Exception {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(10), dp(10), dp(10));
        panel.setBackground(rounded(SURFACE_ALT, 12, BORDER, 1));
        panel.addView(text("همه دسته‌بندی‌ها", 13, BLACK, true), new LinearLayout.LayoutParams(-1, -2));
        addSpace(panel, 6);
        panel.addView(categoryPickerRow(section.optString("allCategoriesLabel", "همه محصولات"), "", 0, selectedCategory, key), new LinearLayout.LayoutParams(-1, dp(40)));

        for (int i = 0; i < categories.length(); i++) {
            JSONObject category = categories.getJSONObject(i);
            int depth = categoryDepth(categories, category);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(40));
            params.setMargins(dp(0), dp(3), dp(0), dp(0));
            panel.addView(categoryPickerRow(categoryLabelWithCount(category), category.getString("id"), depth, selectedCategory, key), params);
        }
        return panel;
    }

    private TextView categoryPickerRow(String label, String categoryId, int depth, String selectedCategory, String key) {
        boolean active = categoryId.equals(selectedCategory);
        TextView row = text(label, 13, active ? WHITE : BLACK, true);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setSingleLine(true);
        row.setEllipsize(TextUtils.TruncateAt.END);
        int inset = dp(12 + Math.min(depth, 3) * 14);
        row.setPadding(inset, dp(0), dp(12), dp(0));
        row.setFocusable(true);
        row.setBackground(interactiveBackground(active ? RED : SURFACE, 10, active ? RED : BORDER, 1));
        attachPressAnimation(row);
        row.setOnClickListener(v -> selectProductCategory(key, categoryId, selectedCategory));
        return row;
    }

    private void selectProductCategory(String key, String categoryId, String selectedCategory) {
        if (categoryId.equals(selectedCategory)) {
            return;
        }
        trackAction("product_category_filter", metadata("category", categoryId.isEmpty() ? "all" : categoryId));
        if (categoryId.isEmpty()) {
            selectedCategories.remove(key);
        } else {
            selectedCategories.put(key, categoryId);
        }
        expandedCategorySections.put(key, false);
        visibleItemCounts.remove(key);
        renderScreen(currentScreen);
    }

    private boolean isRootCategory(JSONObject category) {
        String parentId = category.optString("parentId", "").trim();
        return parentId.isEmpty() || category.optInt("depth", 0) == 0;
    }

    private int rootCategoryCount(JSONArray categories) {
        int count = 0;
        for (int i = 0; i < categories.length(); i++) {
            JSONObject category = categories.optJSONObject(i);
            if (category != null && isRootCategory(category)) {
                count++;
            }
        }
        return count;
    }

    private String categoryLabelWithCount(JSONObject category) {
        String label = category.optString("label", category.optString("treeLabel", ""));
        if (!category.has("productCount")) {
            return label;
        }
        return label + " (" + persianDigits(category.optInt("productCount", 0)) + ")";
    }

    private int categoryDepth(JSONArray categories, JSONObject category) {
        if (category.has("depth")) {
            return Math.max(0, category.optInt("depth", 0));
        }
        int depth = 0;
        String parentId = category.optString("parentId", "").trim();
        while (!parentId.isEmpty() && depth < categories.length()) {
            depth++;
            JSONObject parent = findCategory(categories, parentId);
            parentId = parent == null ? "" : parent.optString("parentId", "").trim();
        }
        return depth;
    }

    private String selectedCategoryPath(JSONArray categories, String selectedCategory) {
        if (selectedCategory == null || selectedCategory.trim().isEmpty()) {
            return "";
        }
        List<String> labels = new ArrayList<>();
        String current = selectedCategory;
        int safety = 0;
        while (!current.isEmpty() && safety++ < categories.length()) {
            JSONObject category = findCategory(categories, current);
            if (category == null) {
                break;
            }
            labels.add(0, category.optString("label", current));
            current = category.optString("parentId", "").trim();
        }
        return labels.isEmpty() ? "" : "دسته: " + TextUtils.join(" / ", labels);
    }

    private View advancedFiltersPanel(JSONObject section, String key, Map<String, String> filtersState, String query) throws Exception {
        JSONArray filters = section.optJSONArray("filters");
        if (filters == null) {
            return null;
        }
        boolean hasAdvanced = false;
        for (int i = 0; i < filters.length(); i++) {
            if (!isQuickFilter(section, filters.getJSONObject(i))) {
                hasAdvanced = true;
                break;
            }
        }
        if (!hasAdvanced) {
            return null;
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(10), dp(10), dp(10));
        box.setBackground(rounded(SURFACE_ALT, 12, BORDER, 1));
        boolean expanded = Boolean.TRUE.equals(expandedFilterSections.get(key));
        int activeControls = activeProductControlCount(section, filtersState, query);
        String label = expanded
                ? section.optString("filtersExpandedLabel", "بستن فیلترها")
                : section.optString("filtersCollapsedLabel", section.optString("filtersTitle", "فیلترهای بیشتر"));
        if (activeControls > 0) {
            label = label + " (" + persianDigits(activeControls) + ")";
        }
        TextView toggle = text(label, 13, activeControls > 0 ? RED : BLACK, true);
        toggle.setGravity(Gravity.CENTER);
        toggle.setSingleLine(true);
        toggle.setPadding(dp(12), dp(0), dp(12), dp(0));
        toggle.setFocusable(true);
        toggle.setBackground(interactiveBackground(SURFACE, 10, activeControls > 0 ? RED : BORDER, 1));
        attachPressAnimation(toggle);
        toggle.setOnClickListener(v -> {
            expandedFilterSections.put(key, !expanded);
            trackAction(expanded ? "product_filters_collapse" : "product_filters_expand");
            renderScreen(currentScreen);
        });
        box.addView(toggle, new LinearLayout.LayoutParams(-1, dp(42)));
        if (!expanded) {
            return box;
        }

        List<JSONObject> numberFilters = new ArrayList<>();
        List<EditText> numberInputs = new ArrayList<>();
        for (int i = 0; i < filters.length(); i++) {
            JSONObject filter = filters.getJSONObject(i);
            if (isQuickFilter(section, filter)) {
                continue;
            }
            addSpace(box, 10);
            if ("number".equals(filter.optString("type", ""))) {
                numberFilters.add(filter);
                EditText input = numberFilterInput(filter, filtersState.get(filter.getString("id")));
                numberInputs.add(input);
                box.addView(input, new LinearLayout.LayoutParams(-1, dp(46)));
            } else {
                box.addView(filterDropdown(filter, key, filtersState.get(filter.getString("id"))), new LinearLayout.LayoutParams(-1, -2));
            }
        }

        addSpace(box, 10);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button apply = button(section.optString("applyFiltersLabel", "اعمال فیلتر"), true);
        apply.setOnClickListener(v -> {
            try {
                boolean changed = false;
                for (int i = 0; i < numberFilters.size(); i++) {
                    changed |= applyProductFilter(numberFilters.get(i), key, numberInputs.get(i).getText().toString(), false);
                }
                if (changed) {
                    renderScreen(currentScreen);
                }
            } catch (Exception e) {
                trackError("product_advanced_filters", e);
                Toast.makeText(this, "خطا در اعمال فیلتر", Toast.LENGTH_SHORT).show();
            }
        });
        actions.addView(apply, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button reset = button(section.optString("resetFiltersLabel", "حذف فیلترها"), false);
        reset.setOnClickListener(v -> clearProductControls(section, key));
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(0, dp(44), 1);
        resetParams.setMargins(dp(4), dp(0), dp(0), dp(0));
        actions.addView(reset, resetParams);
        box.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }

    private EditText numberFilterInput(JSONObject filter, String selectedValue) throws Exception {
        EditText input = new EditText(this);
        input.setText(selectedValue == null ? filter.optString("default", "") : selectedValue);
        input.setHint(filter.getString("label") + " - " + filter.optString("placeholder", ""));
        input.setTextSize(14);
        input.setTextColor(BLACK);
        input.setHintTextColor(MUTED);
        input.setTypeface(bodyTypeface);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setGravity(Gravity.START);
        input.setPadding(dp(12), dp(0), dp(12), dp(0));
        input.setBackground(rounded(SURFACE, 10, BORDER, 1));
        return input;
    }

    private void clearProductControls(JSONObject section, String key) {
        try {
            JSONArray filters = section.optJSONArray("filters");
            if (filters != null) {
                for (int i = 0; i < filters.length(); i++) {
                    selectedFilters.remove(key + ":" + filters.getJSONObject(i).getString("id"));
                }
            }
            searchQueries.remove(key);
            selectedCategories.remove(key);
            visibleItemCounts.remove(key);
            expandedFilterSections.put(key, false);
            expandedCategorySections.put(key, false);
            trackAction("product_filters_reset");
            renderScreen(currentScreen);
        } catch (Exception e) {
            trackError("product_filters_reset", e);
            Toast.makeText(this, "خطا در حذف فیلترها", Toast.LENGTH_SHORT).show();
        }
    }

    private View emptyProductsPanel(JSONObject section, String key) {
        LinearLayout card = panel(SURFACE);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView eyebrow = text("نتیجه‌ای پیدا نشد", 13, RED, true);
        eyebrow.setGravity(Gravity.CENTER);
        card.addView(eyebrow, new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 8);
        TextView title = text(section.optString("emptyStateTitle", "محصولی با این فیلترها نداریم"), 19, BLACK, true);
        title.setGravity(Gravity.CENTER);
        card.addView(title, new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 8);
        TextView body = text(section.optString("emptyStateDescription", section.optString("emptyStateText", "")), 13, MUTED, false);
        body.setGravity(Gravity.CENTER);
        body.setMaxLines(3);
        card.addView(body, new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 14);
        Button reset = button(section.optString("resetFiltersLabel", "حذف فیلترها"), true);
        reset.setOnClickListener(v -> clearProductControls(section, key));
        card.addView(reset, new LinearLayout.LayoutParams(-1, dp(44)));
        addSpace(card, 8);
        Button message = button(section.optString("emptyStateMessageLabel", "ارسال پیام"), false);
        message.setOnClickListener(v -> openScreen("messages"));
        card.addView(message, new LinearLayout.LayoutParams(-1, dp(44)));
        return card;
    }

    private void addProductCards(LinearLayout list, List<JSONObject> items, int visible) throws Exception {
        if (!isWideShopLayout()) {
            for (int i = 0; i < visible; i++) {
                View card = productCard(items.get(i), true);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                params.setMargins(dp(0), dp(4), dp(0), dp(8));
                list.addView(card, params);
            }
            return;
        }

        for (int i = 0; i < visible; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int column = 0; column < 2; column++) {
                int index = i + column;
                View child = index < visible ? productCard(items.get(index), true) : new View(this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
                params.setMargins(column == 0 ? dp(0) : dp(4), dp(4), column == 0 ? dp(4) : dp(0), dp(8));
                row.addView(child, params);
            }
            list.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private boolean isWideShopLayout() {
        return getResources().getConfiguration().screenWidthDp >= 600;
    }

    private void applyProductSearch(String key, String value) {
        trackAction("product_search", metadata("query", value));
        searchQueries.put(key, value);
        visibleItemCounts.remove(key);
        renderScreen(currentScreen);
    }

    private List<JSONObject> filteredProducts(JSONArray source, JSONArray categories, String selectedCategory,
                                              Map<String, String> filters, String query) throws Exception {
        List<JSONObject> result = new ArrayList<>();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.getJSONObject(i);
            String searchable = (item.optString("title", "") + " " + item.optString("subtitle", "") + " " + item.optString("description", "")).toLowerCase(Locale.ROOT);
            boolean matchesSearch = normalizedQuery.isEmpty() || searchable.contains(normalizedQuery);
            if (matchesProductCategory(item, categories, selectedCategory) && matchesFilters(item, filters) && matchesSearch) {
                result.add(item);
            }
        }
        sortProducts(result, filters.get("sort"));
        return result;
    }

    private boolean matchesProductCategory(JSONObject item, JSONArray categories, String selectedCategory) {
        if (selectedCategory == null || selectedCategory.trim().isEmpty()) {
            return true;
        }
        JSONArray path = item.optJSONArray("categoryPath");
        if (path != null) {
            for (int i = 0; i < path.length(); i++) {
                if (selectedCategory.equals(path.optString(i))) {
                    return true;
                }
            }
        }

        String itemCategory = item.optString("category", "");
        if (selectedCategory.equals(itemCategory)) {
            return true;
        }
        return isCategoryDescendant(categories, itemCategory, selectedCategory);
    }

    private boolean isCategoryDescendant(JSONArray categories, String categoryId, String ancestorId) {
        String current = categoryId == null ? "" : categoryId;
        int safety = 0;
        while (!current.isEmpty() && safety++ < categories.length()) {
            if (ancestorId.equals(current)) {
                return true;
            }
            JSONObject category = findCategory(categories, current);
            current = category == null ? "" : category.optString("parentId", "").trim();
        }
        return false;
    }

    private JSONObject findCategory(JSONArray categories, String id) {
        for (int i = 0; i < categories.length(); i++) {
            JSONObject category = categories.optJSONObject(i);
            if (category != null && id.equals(category.optString("id", ""))) {
                return category;
            }
        }
        return null;
    }

    private boolean matchesFilters(JSONObject item, Map<String, String> filters) {
        String availability = filters.containsKey("availability") ? filters.get("availability") : "all";
        if (availability == null) {
            availability = "all";
        }
        if (!"all".equals(availability) && !item.optString("availability", "").equals(availability)) {
            return false;
        }

        String priceBand = filters.containsKey("priceBand") ? filters.get("priceBand") : "all";
        if (priceBand == null) {
            priceBand = "all";
        }
        int price = item.optInt("priceValue", 0);
        switch (priceBand) {
            case "under_2m":
                if (price >= 2000000) return false;
                break;
            case "2m_20m":
                if (price < 2000000 || price > 20000000) return false;
                break;
            case "over_20m":
                if (price <= 20000000) return false;
                break;
            default:
                break;
        }

        int maxPrice = numberFilterValue(filters.get("maxPrice"));
        Map<String, String> featureFilters = new HashMap<>();
        for (Map.Entry<String, String> entry : filters.entrySet()) {
            if (entry.getKey().startsWith("feature:") && entry.getValue() != null && !entry.getValue().trim().isEmpty()) {
                featureFilters.put(entry.getKey().substring("feature:".length()), entry.getValue());
            }
        }

        if (!featureFilters.isEmpty()) {
            return matchesVariantFilters(item, featureFilters, maxPrice);
        }
        return maxPrice <= 0 || matchesAnyPrice(item, maxPrice);
    }

    private int numberFilterValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character >= '0' && character <= '9') {
                digits.append(character);
            } else if (character >= '۰' && character <= '۹') {
                digits.append((char) ('0' + (character - '۰')));
            } else if (character >= '٠' && character <= '٩') {
                digits.append((char) ('0' + (character - '٠')));
            }
        }
        try {
            return digits.length() == 0 ? 0 : Integer.parseInt(digits.toString());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private boolean matchesAnyPrice(JSONObject item, int maxPrice) {
        if (item.optInt("priceValue", 0) <= maxPrice) {
            return true;
        }
        JSONArray variants = item.optJSONArray("variants");
        if (variants == null) {
            return false;
        }
        for (int i = 0; i < variants.length(); i++) {
            JSONObject variant = variants.optJSONObject(i);
            if (variant != null && variant.optInt("priceValue", Integer.MAX_VALUE) <= maxPrice) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesVariantFilters(JSONObject item, Map<String, String> featureFilters, int maxPrice) {
        JSONArray variants = item.optJSONArray("variants");
        if (variants == null) {
            return false;
        }
        for (int i = 0; i < variants.length(); i++) {
            JSONObject variant = variants.optJSONObject(i);
            if (variant == null || (maxPrice > 0 && variant.optInt("priceValue", Integer.MAX_VALUE) > maxPrice)) {
                continue;
            }
            boolean matches = true;
            for (Map.Entry<String, String> entry : featureFilters.entrySet()) {
                if (!entry.getValue().equals(variantFeatureValue(variant, entry.getKey()))) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }

    private String variantFeatureValue(JSONObject variant, String key) {
        JSONObject options = variant.optJSONObject("options");
        if (options == null) {
            return "";
        }
        Object direct = options.opt(key);
        if (direct != null && direct != JSONObject.NULL && !(direct instanceof JSONObject) && !(direct instanceof JSONArray)) {
            return String.valueOf(direct);
        }
        JSONObject attributes = options.optJSONObject("attributes");
        if (attributes == null) {
            return "";
        }
        Object attribute = attributes.opt(key);
        return attribute == null || attribute == JSONObject.NULL || attribute instanceof JSONObject || attribute instanceof JSONArray
                ? ""
                : String.valueOf(attribute);
    }

    private void sortProducts(List<JSONObject> items, String sort) {
        if ("price_low".equals(sort)) {
            Collections.sort(items, this::comparePrices);
        } else if ("price_high".equals(sort)) {
            Collections.sort(items, this::comparePricesDescending);
        } else if ("newest".equals(sort)) {
            Collections.sort(items, this::compareNewestProducts);
        } else {
            Collections.sort(items, this::compareRecommendedProducts);
        }
    }

    private int compareRecommendedProducts(JSONObject left, JSONObject right) {
        boolean leftFeatured = left.optBoolean("isFeatured", false);
        boolean rightFeatured = right.optBoolean("isFeatured", false);
        if (leftFeatured != rightFeatured) {
            return leftFeatured ? -1 : 1;
        }
        boolean leftHasSortOrder = left.has("sortOrder");
        boolean rightHasSortOrder = right.has("sortOrder");
        if (!leftHasSortOrder && !rightHasSortOrder) {
            return 0;
        }
        if (leftHasSortOrder != rightHasSortOrder) {
            return leftHasSortOrder ? -1 : 1;
        }
        int sortOrder = Integer.compare(left.optInt("sortOrder", 0), right.optInt("sortOrder", 0));
        if (sortOrder != 0) {
            return sortOrder;
        }
        return left.optString("title", "").compareTo(right.optString("title", ""));
    }

    private int compareNewestProducts(JSONObject left, JSONObject right) {
        String leftDate = left.optString("createdAt", "");
        String rightDate = right.optString("createdAt", "");
        int dateComparison = rightDate.compareTo(leftDate);
        return dateComparison != 0 ? dateComparison : compareRecommendedProducts(left, right);
    }

    private int comparePricesDescending(JSONObject left, JSONObject right) {
        return comparePrices(right, left);
    }

    private int comparePrices(JSONObject left, JSONObject right) {
        int leftPrice = left.optInt("priceValue", 0);
        int rightPrice = right.optInt("priceValue", 0);
        if (leftPrice == rightPrice) return 0;
        return leftPrice < rightPrice ? -1 : 1;
    }

    private Map<String, String> currentFilterValues(JSONObject section, String key) throws Exception {
        Map<String, String> values = new HashMap<>();
        if (!section.has("filters")) return values;

        JSONArray filters = section.getJSONArray("filters");
        for (int i = 0; i < filters.length(); i++) {
            JSONObject filter = filters.getJSONObject(i);
            String id = filter.getString("id");
            String stateKey = key + ":" + id;
            String selectedValue = selectedFilters.get(stateKey);
            values.put(id, selectedValue == null ? filter.optString("default", "all") : selectedValue);
        }
        return values;
    }

    private int activeFilterCount(JSONObject section, Map<String, String> filtersState) throws Exception {
        int count = 0;
        JSONArray filters = section.getJSONArray("filters");
        for (int i = 0; i < filters.length(); i++) {
            JSONObject filter = filters.getJSONObject(i);
            String id = filter.getString("id");
            String value = filtersState.containsKey(id) ? filtersState.get(id) : filter.optString("default", "all");
            if (value == null) {
                value = filter.optString("default", "all");
            }
            if (!isDefaultFilter(filter, value)) {
                count++;
            }
        }
        return count;
    }

    private int activeProductControlCount(JSONObject section, Map<String, String> filtersState, String query) throws Exception {
        int count = activeFilterCount(section, filtersState);
        return query == null || query.trim().isEmpty() ? count : count + 1;
    }

    private String filterOptionLabel(JSONObject filter, String value) throws Exception {
        JSONArray options = filter.optJSONArray("options");
        if (options == null) {
            return value == null || value.isEmpty() ? filter.optString("label", "") : value;
        }
        for (int i = 0; i < options.length(); i++) {
            JSONObject option = options.getJSONObject(i);
            if (option.optString("id", "").equals(value)) {
                return option.optString("label", value);
            }
        }
        return value == null ? "" : value;
    }

    private View filterDropdown(JSONObject filter, String key, String selectedValue) throws Exception {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.START);

        box.addView(text(filter.getString("label"), 13, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        addSpace(box, 4);

        JSONArray options = filter.getJSONArray("options");
        List<String> labels = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        int selectedIndex = 0;
        String currentValue = selectedValue == null ? filter.optString("default", "all") : selectedValue;
        for (int i = 0; i < options.length(); i++) {
            JSONObject option = options.getJSONObject(i);
            ids.add(option.getString("id"));
            labels.add(option.getString("label"));
            if (option.getString("id").equals(currentValue)) {
                selectedIndex = i;
            }
        }

        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selectedIndex, false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                try {
                    applyProductFilter(filter, key, ids.get(position), true);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "خطا در اعمال فیلتر", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        box.addView(spinner, new LinearLayout.LayoutParams(-1, dp(46)));
        return box;
    }

    private boolean isDefaultFilter(JSONObject filter, String value) {
        return filter.optString("default", "all").equals(value);
    }

    private View listCards(JSONArray items) throws Exception {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < items.length(); i++) {
            View card = productCard(items.getJSONObject(i), false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(dp(0), dp(5), dp(0), dp(7));
            list.addView(card, params);
        }
        return list;
    }

    private View clientDetails(JSONObject section) throws Exception {
        LinearLayout card = panel(SURFACE);
        JSONArray fields = section.getJSONArray("fields");
        for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.getJSONObject(i);
            card.addView(text(field.getString("label"), 12, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
            addSpace(card, 2);
            card.addView(text(field.getString("value"), 15, BLACK, true), new LinearLayout.LayoutParams(-1, -2));
            if (i < fields.length() - 1) addSpace(card, 10);
        }
        return card;
    }

    private View accountList(JSONArray items) throws Exception {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            View card = accountCard(item);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(dp(0), dp(5), dp(0), dp(7));
            list.addView(card, params);
        }
        return list;
    }

    private View ongoingPurchases(JSONObject section) throws Exception {
        JSONObject data = sectionData(section);
        JSONArray items = data.getJSONArray("items");
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        if (items.length() == 0) {
            list.addView(infoText(data.optString("emptyStateText", "")));
            return list;
        }

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String key = currentScreen + ":" + section.optString("id", data.optString("title", "ongoing")) + ":" + item.optString("id", i + "");
            View card = ongoingPurchaseCard(item, key);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(dp(0), dp(5), dp(0), dp(7));
            list.addView(card, params);
        }
        return list;
    }

    private View accountCard(JSONObject item) throws Exception {
        LinearLayout card = panel(WHITE);
        card.addView(text(item.getString("title"), 17, BLACK, true), new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 5);
        card.addView(text(item.getString("subtitle"), 13, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        String description = item.optString("description", "");
        if (!description.isEmpty()) {
            addSpace(card, 5);
            card.addView(text(description, 12, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        }
        addSpace(card, 8);
        card.addView(text(item.optString("price", ""), 14, ACCENT, true), new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    private View ongoingPurchaseCard(JSONObject item, String key) throws Exception {
        LinearLayout card = panel(WHITE);
        card.addView(text(item.getString("title"), 17, BLACK, true), new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 5);
        card.addView(text(item.getString("subtitle"), 13, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 8);
        card.addView(text(item.optString("status", ""), 14, ACCENT, true), new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 10);

        boolean expanded = Boolean.TRUE.equals(expandedAccountSections.get(key));
        Button toggle = button(expanded ? item.optString("collapseLabel", "بستن وضعیت") : item.optString("expandLabel", "مشاهده وضعیت"), false);
        toggle.setOnClickListener(v -> {
            trackAction(expanded ? "status_collapse" : "status_expand", metadata("item", item.optString("id", "")));
            expandedAccountSections.put(key, !expanded);
            renderScreen(currentScreen);
        });
        card.addView(toggle, new LinearLayout.LayoutParams(-1, dp(44)));

        if (expanded) {
            addSpace(card, 12);
            card.addView(text(item.optString("currentLocation", ""), 13, BLACK, true), new LinearLayout.LayoutParams(-1, -2));
            addSpace(card, 8);
            JSONArray steps = item.getJSONArray("steps");
            int currentStep = item.optInt("currentStep", 0);
            for (int i = 0; i < steps.length(); i++) {
                JSONObject step = steps.getJSONObject(i);
                boolean active = i == currentStep;
                boolean done = i < currentStep;
                String prefix = done ? "✓ " : active ? "• " : "○ ";
                int color = active ? RED : done ? BLACK : MUTED;
                card.addView(text(prefix + step.getString("label"), 14, color, active || done), new LinearLayout.LayoutParams(-1, -2));
                String detail = step.optString("detail", "");
                if (!detail.isEmpty()) {
                    card.addView(text(detail, 12, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
                }
                if (i < steps.length() - 1) addSpace(card, 8);
            }
        }
        return card;
    }

    private View smallCard(JSONObject item) throws Exception {
        LinearLayout card = panel(SURFACE);
        card.setBackground(interactiveBackground(SURFACE, 8, BORDER, 1));
        card.setFocusable(true);
        attachPressAnimation(card);
        String badge = item.optString("badge", "");
        if (!badge.isEmpty()) {
            TextView badgeView = pillText(badge, 11, ACCENT, RED_TINT, ACCENT);
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(-2, dp(28));
            badgeParams.setMargins(dp(0), dp(0), dp(0), dp(8));
            card.addView(badgeView, badgeParams);
        }
        card.addView(text(item.getString("title"), 16, BLACK, true), new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 6);
        card.addView(text(item.getString("subtitle"), 12, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        String target = item.optString("target", "shop");
        card.setOnClickListener(v -> {
            trackAction("shortcut_card", metadata("target", target));
            openScreen(target);
        });
        return card;
    }

    private View productCard(JSONObject item, boolean compact) throws Exception {
        boolean productLike = isProductLike(item);
        LinearLayout card = panel(WHITE);
        card.setBackground(productLike ? interactiveBackground(SURFACE, 10, BORDER, 1) : rounded(SURFACE, 10, BORDER, 1));
        if (productLike) {
            card.setFocusable(true);
            attachPressAnimation(card);
            card.setOnClickListener(v -> openProductDetail(item));
        }

        int imageHeight = dp(compact ? FEATURED_CARD_MEDIA_HEIGHT_DP : CARD_MEDIA_HEIGHT_DP);
        card.addView(thumbnail(item, imageHeight), new LinearLayout.LayoutParams(-1, imageHeight));
        addSpace(card, 12);

        LinearLayout detail = new LinearLayout(this);
        detail.setOrientation(LinearLayout.VERTICAL);
        detail.setGravity(Gravity.START);
        if (productLike) {
            LinearLayout meta = new LinearLayout(this);
            meta.setOrientation(LinearLayout.HORIZONTAL);
            meta.setGravity(Gravity.CENTER_VERTICAL);
            TextView category = pillText(productCategoryLabel(item), 11, RED, RED_TINT, RED);
            LinearLayout.LayoutParams categoryParams = new LinearLayout.LayoutParams(0, dp(30), 1);
            categoryParams.setMargins(dp(0), dp(0), dp(6), dp(0));
            meta.addView(category, categoryParams);
            meta.addView(productAvailabilityPill(item), new LinearLayout.LayoutParams(-2, dp(30)));
            detail.addView(meta, new LinearLayout.LayoutParams(-1, -2));
            addSpace(detail, 8);
        }
        TextView title = text(item.getString("title"), compact ? 19 : 17, BLACK, true);
        title.setMaxLines(2);
        detail.addView(title, new LinearLayout.LayoutParams(-1, -2));
        addSpace(detail, 5);
        detail.addView(text(item.getString("subtitle"), 13, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        String description = item.optString("description", "");
        if (!description.isEmpty()) {
            addSpace(detail, 5);
            TextView desc = text(description, 12, MUTED, false);
            desc.setMaxLines(compact ? 3 : 2);
            desc.setEllipsize(TextUtils.TruncateAt.END);
            detail.addView(desc, new LinearLayout.LayoutParams(-1, -2));
        }
        addSpace(detail, 8);
        detail.addView(text(item.optString("price", ""), 17, BLACK, true), new LinearLayout.LayoutParams(-1, -2));

        card.addView(detail, new LinearLayout.LayoutParams(-1, -2));

        addSpace(card, 12);
        if (productLike) {
            boolean unavailable = isProductUnavailable(item);
            Button action = button(unavailable ? "ناموجود" : (item.has("priceValue") ? "افزودن به سبد" : "افزودن / رزرو"), true);
            if (unavailable) {
                disableProductAction(action);
            } else {
                action.setOnClickListener(v -> {
                    trackAction("add_to_cart", metadata("product", item.optString("id", item.optString("title", ""))));
                    addProductToCart(item);
                });
            }
            card.addView(action, new LinearLayout.LayoutParams(-1, dp(44)));
        } else {
            Button action = button(item.has("priceValue") ? "افزودن به سبد" : "افزودن / رزرو", true);
            action.setOnClickListener(v -> {
                trackAction("add_to_cart", metadata("product", item.optString("id", item.optString("title", ""))));
                addProductToCart(item);
            });
            card.addView(action, new LinearLayout.LayoutParams(-1, dp(44)));
        }
        return card;
    }

    private boolean isProductLike(JSONObject item) {
        return item.has("category") || item.has("priceValue") || item.has("stockLabel");
    }

    private void addProductToCart(JSONObject item) {
        if (isProductUnavailable(item)) {
            Toast.makeText(this, "این محصول در حال حاضر ناموجود است", Toast.LENGTH_SHORT).show();
            return;
        }
        String productId = item.optString("id", "").trim();
        String url = remoteUrl("cartItemsUrl", "/cart/items");

        if (url.isEmpty() || productId.isEmpty()) {
            cacheCartItem(item);
            cartCount++;
            updateCartButton();
            pulseView(cartButton);
            Toast.makeText(this, "به سبد اضافه شد", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("device_id", telemetryDeviceId);
            payload.put("product", productId);
            payload.put("quantity", 1);

            new Thread(() -> {
                try {
                    JSONObject response = new JSONObject(postJson(url, payload.toString()));
                    JSONObject data = response.optJSONObject("data");
                    int nextCount = data == null ? cartCount + 1 : data.optInt("count", cartCount + 1);
                    runOnUiThread(() -> {
                        if (data != null) {
                            applyCartPayload(data);
                        } else {
                            cacheCartItem(item);
                            cartCount = nextCount;
                        }
                        updateCartButton();
                        pulseView(cartButton);
                        Toast.makeText(this, "به سبد اضافه شد", Toast.LENGTH_SHORT).show();
                        if ("cart".equals(currentScreen)) {
                            renderScreen("cart", false);
                        }
                    });
                } catch (Exception e) {
                    trackError("add_to_cart", e);
                    runOnUiThread(() -> Toast.makeText(this, "خطا در افزودن به سبد", Toast.LENGTH_SHORT).show());
                }
            }).start();
        } catch (Exception e) {
            trackError("add_to_cart_payload", e);
            Toast.makeText(this, "خطا در افزودن به سبد", Toast.LENGTH_SHORT).show();
        }
    }

    private void cacheCartItem(JSONObject item) {
        String id = item.optString("id", item.optString("title", ""));
        if (id.isEmpty()) return;

        try {
            JSONObject cached = cartItems.get(id);
            if (cached == null) {
                cached = new JSONObject(item.toString());
            }
            cached.put("quantity", cached.optInt("quantity", 0) + 1);
            cartItems.put(id, cached);
        } catch (Exception ignored) {
        }
    }

    private void applyCartPayload(JSONObject data) {
        try {
            cartItems.clear();
            cartItems.putAll(cartItemsFromPayload(data));
            cartCount = data.optInt("count", cartQuantity());
            lastCartRefreshAt = System.currentTimeMillis();
            updateCartButton();
        } catch (Exception e) {
            trackError("cart_payload", e);
        }
    }

    private Map<String, JSONObject> cartItemsFromPayload(JSONObject data) throws Exception {
        Map<String, JSONObject> result = new LinkedHashMap<>();
        JSONArray items = data.optJSONArray("items");
        if (items == null) {
            return result;
        }

        for (int i = 0; i < items.length(); i++) {
            JSONObject cartItem = items.getJSONObject(i);
            JSONObject product = cartItem.optJSONObject("product");
            if (product == null) continue;

            product.put("cartItemId", cartItem.optInt("id", 0));
            product.put("quantity", cartItem.optInt("quantity", 1));
            product.put("lineTotal", cartItem.optInt("line_total", cartLineTotal(product)));
            String id = product.optString("id", "");
            if (!id.isEmpty()) {
                result.put(id, product);
            }
        }

        return result;
    }

    private int cartQuantity() {
        int count = 0;
        for (JSONObject item : cartItems.values()) {
            count += Math.max(1, item.optInt("quantity", 1));
        }
        return count;
    }

    private int cartSubtotal() {
        int total = 0;
        for (JSONObject item : cartItems.values()) {
            total += cartLineTotal(item);
        }
        return total;
    }

    private int cartLineTotal(JSONObject item) {
        int quantity = Math.max(1, item.optInt("quantity", 1));
        if (item.has("lineTotal")) {
            return Math.max(0, item.optInt("lineTotal", 0));
        }

        return Math.max(0, item.optInt("priceValue", 0)) * quantity;
    }

    private String formatToman(int value) {
        String digits = String.valueOf(Math.max(0, value));
        StringBuilder grouped = new StringBuilder();
        int count = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            if (count > 0 && count % 3 == 0) {
                grouped.insert(0, ',');
            }
            grouped.insert(0, digits.charAt(i));
            count++;
        }
        return persianDigits(grouped.toString()) + " تومان";
    }

    private View thumbnail(JSONObject item, int height) {
        String imageUrl = item.optString("imageUrl", "").trim();
        TextView fallback = text(item.optString("thumbnailText", "ETOK"), 18, WHITE, true);
        fallback.setGravity(Gravity.CENTER);
        fallback.setSingleLine(true);
        fallback.setEllipsize(TextUtils.TruncateAt.END);
        fallback.setBackground(rounded(item.optString("thumbnailColor", "#101114")));
        fallback.setMinHeight(height);

        if (imageUrl.isEmpty()) {
            return fallback;
        }

        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(rounded(item.optString("thumbnailColor", "#101114")));
        frame.addView(fallback, new FrameLayout.LayoutParams(-1, -1));

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setVisibility(View.INVISIBLE);
        frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
        loadRemoteImage(image, imageUrl);
        return frame;
    }

    private void loadRemoteImage(ImageView image, String urlValue) {
        new Thread(() -> {
            try {
                Bitmap bitmap = downloadBitmapWithFallbacks(urlValue);
                if (bitmap == null) return;
                runOnUiThread(() -> {
                    image.setImageBitmap(bitmap);
                    image.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                trackError("image_load", e);
            }
        }).start();
    }

    private View infoPanel(JSONObject section) throws Exception {
        LinearLayout card = panel(SURFACE);
        card.addView(text(section.getString("title"), 18, BLACK, true), new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 7);
        card.addView(text(section.getString("subtitle"), 14, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    private View infoText(String value) {
        LinearLayout card = panel(SURFACE);
        card.setPadding(dp(14), dp(16), dp(14), dp(16));
        TextView title = text("موردی پیدا نشد", 16, BLACK, true);
        card.addView(title, new LinearLayout.LayoutParams(-1, -2));
        if (value != null && !value.trim().isEmpty()) {
            addSpace(card, 6);
            TextView body = text(value, 13, MUTED, false);
            body.setMaxLines(3);
            card.addView(body, new LinearLayout.LayoutParams(-1, -2));
        }
        return card;
    }

    private TextView sectionTitle(String value) {
        TextView title = text(value, 19, BLACK, true);
        title.setPadding(dp(0), dp(8), dp(0), dp(10));
        return title;
    }

    private TextView pillText(String value, int sp, int textColor, int fillColor, int strokeColor) {
        TextView view = text(value, sp, textColor, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setPadding(dp(12), dp(0), dp(12), dp(0));
        view.setBackground(rounded(fillColor, 18, strokeColor, 1));
        return view;
    }

    private LinearLayout panel(int color) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.START);
        box.setPadding(dp(16), dp(16), dp(16), dp(16));
        box.setBackground(rounded(color, 10, BORDER, 1));
        box.setElevation(dp(1));
        return box;
    }

    private GradientDrawable rounded(String color) {
        return rounded(Color.parseColor(color), 10, 0, 0);
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0) {
            drawable.setStroke(dp(strokeWidth), strokeColor);
        }
        return drawable;
    }

    private Drawable interactiveBackground(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable content = rounded(color, radius, strokeColor, strokeWidth);
        int rippleColor = color == RED
                ? Color.argb(42, 255, 255, 255)
                : Color.argb(28, Color.red(RED), Color.green(RED), Color.blue(RED));
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, null);
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.START);
        view.setIncludeFontPadding(false);
        view.setLineSpacing(dp(2), 1.0f);
        view.setTypeface(bold ? emphasisTypeface : bodyTypeface);
        return view;
    }

    private Button button(String label, boolean filled) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setTypeface(emphasisTypeface);
        button.setIncludeFontPadding(false);
        button.setAllCaps(false);
        button.setTextColor(filled ? WHITE : RED);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(14), dp(0), dp(14), dp(0));
        button.setBackground(interactiveBackground(filled ? RED : SURFACE, 10, filled ? RED : BORDER, 1));
        button.setGravity(Gravity.CENTER);
        button.setFocusable(true);
        button.setSingleLine(false);
        button.setMaxLines(2);
        attachPressAnimation(button);
        return button;
    }

    private Button topIconButton(int iconResource) {
        Button action = button("", false);
        action.setTextColor(WHITE);
        action.setTextSize(13);
        action.setBackground(interactiveBackground(RED, 14, DARK_RED, 1));
        action.setCompoundDrawablesWithIntrinsicBounds(iconResource, 0, 0, 0);
        action.setCompoundDrawablePadding(dp(4));
        return action;
    }

    private void animateScreenEntrance(boolean animate) {
        if (!animate || content == null) return;

        int childCount = Math.min(content.getChildCount(), SCREEN_ANIMATION_CHILD_LIMIT);
        for (int i = 0; i < childCount; i++) {
            View child = content.getChildAt(i);
            child.animate().cancel();
            child.setAlpha(0f);
            child.setTranslationY(dp(10));
            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(i * SCREEN_STAGGER_MS)
                    .setDuration(SCREEN_ANIMATION_MS)
                    .setInterpolator(EASE_OUT)
                    .start();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void attachPressAnimation(View view) {
        if (view == null) return;

        view.setOnTouchListener((target, event) -> {
            if (!target.isEnabled()) return false;

            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                target.animate().cancel();
                target.animate()
                        .scaleX(0.97f)
                        .scaleY(0.97f)
                        .setDuration(PRESS_ANIMATION_MS)
                        .setInterpolator(EASE_OUT)
                        .start();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                target.animate().cancel();
                target.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(PRESS_ANIMATION_MS)
                        .setInterpolator(EASE_OUT)
                        .start();
            }
            return false;
        });
    }

    private void pulseView(View view) {
        if (view == null) return;

        view.animate().cancel();
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.animate()
                .scaleX(1.08f)
                .scaleY(1.08f)
                .setDuration(100L)
                .setInterpolator(EASE_OUT)
                .withEndAction(() -> view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(140L)
                        .setInterpolator(EASE_OUT)
                        .start())
                .start();
    }

    private void updateCartButton() {
        if (cartButton != null) {
            cartButton.setText(cartCount > 0 ? persianDigits(cartCount) : "");
            cartButton.setContentDescription(cartCount > 0 ? "سبد خرید، " + persianDigits(cartCount) + " آیتم" : "سبد خرید");
        }
    }

    private void updateMessageButton() {
        if (messagesButton != null) {
            messagesButton.setText(unreadMessageCount > 0 ? persianDigits(unreadMessageCount) : "");
            messagesButton.setContentDescription(unreadMessageCount > 0 ? "پیام‌ها، " + persianDigits(unreadMessageCount) + " پیام خوانده نشده" : "پیام‌ها");
        }
    }

    private void refreshMobileState() {
        String url = remoteUrl("stateUrl", "/mobile/state");
        if (url.isEmpty() || telemetryDeviceId == null || telemetryDeviceId.trim().isEmpty()) {
            return;
        }

        String separator = url.contains("?") ? "&" : "?";
        String requestUrl = url + separator + "device_id=" + urlEncode(telemetryDeviceId);

        new Thread(() -> {
            try {
                JSONObject response = new JSONObject(downloadTextWithFallbacks(requestUrl));
                JSONObject data = response.optJSONObject("data");
                if (data == null) return;

                int nextCartCount = data.optInt("cart_count", cartCount);
                int nextUnreadCount = data.optInt("unread_message_count", unreadMessageCount);
                Map<String, JSONObject> nextCartItems = fetchCartItems();
                runOnUiThread(() -> {
                    cartCount = nextCartCount;
                    unreadMessageCount = nextUnreadCount;
                    if (nextCartItems != null) {
                        cartItems.clear();
                        cartItems.putAll(nextCartItems);
                    }
                    updateCartButton();
                    updateMessageButton();
                });
            } catch (Exception e) {
                trackError("mobile_state", e);
            }
        }).start();
    }

    private void refreshCartForScreen() {
        if (!"cart".equals(currentScreen)) {
            return;
        }

        String url = remoteUrl("cartUrl", "/cart");
        if (url.isEmpty() || telemetryDeviceId == null || telemetryDeviceId.trim().isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (cartRefreshInProgress || now - lastCartRefreshAt < 5000L) {
            return;
        }

        cartRefreshInProgress = true;
        new Thread(() -> {
            try {
                Map<String, JSONObject> nextCartItems = fetchCartItems();
                runOnUiThread(() -> {
                    cartRefreshInProgress = false;
                    lastCartRefreshAt = System.currentTimeMillis();
                    if (nextCartItems != null) {
                        cartItems.clear();
                        cartItems.putAll(nextCartItems);
                        cartCount = cartQuantity();
                        updateCartButton();
                        if ("cart".equals(currentScreen)) {
                            renderScreen("cart", false);
                        }
                    }
                });
            } catch (Exception e) {
                cartRefreshInProgress = false;
                trackError("cart_screen_refresh", e);
            }
        }).start();
    }

    private Map<String, JSONObject> fetchCartItems() {
        String url = remoteUrl("cartUrl", "/cart");
        if (url.isEmpty()) {
            return null;
        }

        try {
            String separator = url.contains("?") ? "&" : "?";
            JSONObject response = new JSONObject(downloadTextWithFallbacks(url + separator + "device_id=" + urlEncode(telemetryDeviceId)));
            JSONObject data = response.optJSONObject("data");
            if (data == null) return null;

            return cartItemsFromPayload(data);
        } catch (Exception e) {
            trackError("cart_fetch", e);
            return null;
        }
    }

    private String remoteUrl(String key, String fallbackPath) {
        JSONObject remoteConfig = remoteConfig();
        if (remoteConfig == null) {
            return "";
        }

        String configured = withActiveManifestBase(remoteConfig.optString(key, "").trim());
        if (!configured.isEmpty()) {
            return configured;
        }

        String manifestUrl = withActiveManifestBase(remoteConfig.optString("manifestUrl", "").trim());
        if (manifestUrl.endsWith("/mobile/manifest")) {
            return manifestUrl.substring(0, manifestUrl.length() - "/mobile/manifest".length()) + fallbackPath;
        }

        if (manifestUrl.endsWith("/manifest")) {
            return manifestUrl.substring(0, manifestUrl.length() - "/manifest".length()) + fallbackPath;
        }

        return "";
    }

    @SuppressWarnings("CharsetObjectCanBeUsed")
    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (Exception ignored) {
            return value;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String persianDigits(int value) {
        return persianDigits(String.valueOf(value));
    }

    private String persianDigits(String value) {
        char[] digits = value.toCharArray();
        StringBuilder builder = new StringBuilder();
        for (char digit : digits) {
            if (digit >= '0' && digit <= '9') {
                builder.append((char) ('۰' + (digit - '0')));
            } else {
                builder.append(digit);
            }
        }
        return builder.toString();
    }

    private void addSpace(LinearLayout parent, int dp) {
        View space = new View(this);
        parent.addView(space, new LinearLayout.LayoutParams(1, dp(dp)));
    }

    private String downloadText(String urlValue) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlValue).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        addAuthHeader(connection);

        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new IllegalStateException("HTTP " + code);
        }

        try {
            return readStream(connection.getInputStream());
        } finally {
            connection.disconnect();
        }
    }

    private Bitmap downloadBitmap(String urlValue) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlValue).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestMethod("GET");

        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new IllegalStateException("HTTP " + code);
        }

        try {
            return BitmapFactory.decodeStream(connection.getInputStream());
        } finally {
            connection.disconnect();
        }
    }

    private String postJson(String urlValue, String json) throws Exception {
        return requestJson("POST", urlValue, json);
    }

    private String patchJson(String urlValue, String json) throws Exception {
        return requestJson("PATCH", urlValue, json);
    }

    private String deleteJson(String urlValue, String json) throws Exception {
        return requestJson("DELETE", urlValue, json);
    }

    private String requestJson(String method, String urlValue, String json) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlValue).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        addAuthHeader(connection);
        connection.setDoOutput(true);

        try (OutputStream output = connection.getOutputStream()) {
            output.write(json.getBytes(StandardCharsets.UTF_8));
        }

        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new IllegalStateException("HTTP " + code);
        }

        try {
            return readStream(connection.getInputStream());
        } finally {
            connection.disconnect();
        }
    }

    private void addAuthHeader(HttpURLConnection connection) {
        if (isLoggedIn()) {
            connection.setRequestProperty("Authorization", "Bearer " + authToken.trim());
        }
    }

    private String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) builder.append('0');
            builder.append(hex);
        }
        return builder.toString();
    }

    private String readAsset(String path) throws Exception {
        return readStream(getAssets().open(path));
    }

    private String readStream(InputStream input) throws Exception {
        try (InputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = source.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private ViewGroup.LayoutParams match() {
        return new ViewGroup.LayoutParams(-1, -1);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class LoadingIntroView extends View {
        private static final long CYCLE_MS = 1750L;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Typeface bodyTypeface;
        private final Typeface emphasisTypeface;
        private long startedAt;
        private boolean running;

        LoadingIntroView(Context context) {
            super(context);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                bodyTypeface = context.getResources().getFont(R.font.vazirmatn_regular);
                emphasisTypeface = context.getResources().getFont(R.font.vazirmatn_semibold);
            } else {
                bodyTypeface = Typeface.create("sans-serif", Typeface.NORMAL);
                emphasisTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL);
            }
            setBackgroundColor(WHITE);
            setContentDescription("EtokBike در حال بارگذاری");
            setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        }

        void start() {
            startedAt = SystemClock.uptimeMillis();
            running = true;
            postInvalidateOnAnimation();
        }

        void stop() {
            running = false;
        }

        @Override
        @SuppressWarnings("NullableProblems")
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) {
                return;
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(WHITE);
            canvas.drawRect(0, 0, width, height, paint);

            float centerX = width / 2f;
            float brandY = height * 0.28f;

            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(emphasisTypeface);
            paint.setTextSize(d(30));
            paint.setColor(BLACK);
            canvas.drawText("EtokBike", centerX, brandY, paint);

            paint.setTypeface(bodyTypeface);
            paint.setTextSize(d(14));
            paint.setColor(MUTED);
            canvas.drawText("در حال آماده‌سازی فروشگاه", centerX, brandY + d(30), paint);

            float roadY = height * 0.62f;
            paint.setStrokeWidth(d(2));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(BORDER);
            canvas.drawLine(d(28), roadY, width - d(28), roadY, paint);

            long elapsed = SystemClock.uptimeMillis() - startedAt;
            float rawProgress = (elapsed % CYCLE_MS) / (float) CYCLE_MS;
            float progress = (float) (0.5f - Math.cos(rawProgress * Math.PI) / 2f);
            float cyclistWidth = d(84);
            float x = -cyclistWidth + (width + cyclistWidth * 2f) * progress;
            float bob = (float) Math.sin(rawProgress * Math.PI * 4f) * d(2);

            drawCyclist(canvas, x, roadY + bob);
            drawProgressDots(canvas, centerX, roadY + d(66), rawProgress);

            if (running) {
                postInvalidateOnAnimation();
            }
        }

        private void drawCyclist(Canvas canvas, float x, float roadY) {
            float wheelRadius = d(15);
            float frontX = x + d(58);
            float wheelY = roadY - wheelRadius;
            float crankX = x + d(28);
            float crankY = wheelY - d(16);
            float seatX = x + d(24);
            float seatY = wheelY - d(38);
            float handleX = x + d(49);
            float handleY = wheelY - d(38);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(d(3));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(BLACK);
            canvas.drawCircle(x, wheelY, wheelRadius, paint);
            canvas.drawCircle(frontX, wheelY, wheelRadius, paint);

            paint.setColor(RED);
            canvas.drawLine(x, wheelY, crankX, crankY, paint);
            canvas.drawLine(crankX, crankY, frontX, wheelY, paint);
            canvas.drawLine(crankX, crankY, seatX, seatY, paint);
            canvas.drawLine(seatX, seatY, x, wheelY, paint);
            canvas.drawLine(seatX, seatY, handleX, handleY, paint);
            canvas.drawLine(handleX, handleY, frontX, wheelY, paint);

            paint.setColor(BLACK);
            canvas.drawLine(seatX - d(9), seatY - d(2), seatX + d(8), seatY - d(2), paint);
            canvas.drawLine(handleX, handleY, handleX + d(12), handleY - d(4), paint);

            float headX = x + d(32);
            float headY = wheelY - d(67);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(BLACK);
            canvas.drawCircle(headX, headY, d(7), paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(d(4));
            paint.setColor(RED);
            canvas.drawLine(headX - d(2), headY + d(10), seatX + d(7), seatY + d(8), paint);
            canvas.drawLine(seatX + d(7), seatY + d(8), handleX + d(7), handleY, paint);
            canvas.drawLine(seatX + d(7), seatY + d(8), crankX + d(3), crankY, paint);
            canvas.drawLine(crankX + d(3), crankY, crankX - d(13), crankY + d(15), paint);
        }

        private void drawProgressDots(Canvas canvas, float centerX, float y, float progress) {
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 3; i++) {
                float distance = Math.abs(progress - (i / 3f));
                float alpha = distance < 0.18f ? 1f : 0.35f;
                paint.setColor(Color.argb((int) (255 * alpha), Color.red(RED), Color.green(RED), Color.blue(RED)));
                canvas.drawCircle(centerX + (i - 1) * d(18), y, d(4), paint);
            }
        }

        private int d(int value) {
            return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
        }
    }

    private static class ConfigDatabase extends SQLiteOpenHelper {
        private static final String DB_NAME = "server_driven_ui.db";
        private static final int DB_VERSION = 1;

        ConfigDatabase(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE app_manifest (id INTEGER PRIMARY KEY CHECK (id = 1), app_version INTEGER NOT NULL, raw_json TEXT NOT NULL, updated_at INTEGER NOT NULL)");
            db.execSQL("CREATE TABLE screen_configs (screen_id TEXT PRIMARY KEY, version INTEGER NOT NULL, raw_json TEXT NOT NULL, updated_at INTEGER NOT NULL)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS app_manifest");
            db.execSQL("DROP TABLE IF EXISTS screen_configs");
            onCreate(db);
        }

        String getManifestJson() {
            SQLiteDatabase db = getReadableDatabase();
            try (Cursor cursor = db.rawQuery("SELECT raw_json FROM app_manifest WHERE id = 1", null)) {
                return cursor.moveToFirst() ? cursor.getString(0) : null;
            }
        }

        void saveManifest(JSONObject manifest, int version) {
            ContentValues values = new ContentValues();
            values.put("id", 1);
            values.put("app_version", version);
            values.put("raw_json", manifest.toString());
            values.put("updated_at", System.currentTimeMillis());
            getWritableDatabase().replace("app_manifest", null, values);
        }

        ScreenCacheEntry getScreenCache(String screenId) {
            SQLiteDatabase db = getReadableDatabase();
            try (Cursor cursor = db.rawQuery("SELECT raw_json, version FROM screen_configs WHERE screen_id = ?", new String[]{screenId})) {
                if (!cursor.moveToFirst()) {
                    return null;
                }
                return new ScreenCacheEntry(cursor.getString(0), cursor.getInt(1));
            }
        }

        int getScreenVersion(String screenId) {
            SQLiteDatabase db = getReadableDatabase();
            try (Cursor cursor = db.rawQuery("SELECT version FROM screen_configs WHERE screen_id = ?", new String[]{screenId})) {
                return cursor.moveToFirst() ? cursor.getInt(0) : 0;
            }
        }

        void saveScreen(String screenId, JSONObject screen, int version) {
            ContentValues values = new ContentValues();
            values.put("screen_id", screenId);
            values.put("version", version);
            values.put("raw_json", screen.toString());
            values.put("updated_at", System.currentTimeMillis());
            getWritableDatabase().replace("screen_configs", null, values);
        }

        private static class ScreenCacheEntry {
            final String rawJson;
            final int version;

            ScreenCacheEntry(String rawJson, int version) {
                this.rawJson = rawJson;
                this.version = version;
            }
        }
    }
}
