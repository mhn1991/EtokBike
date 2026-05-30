package com.etokbike.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String BUNDLED_MANIFEST_PATH = "mock/manifest.json";
    private static final int SUPPORTED_SCHEMA_VERSION = 1;

    private static final int RED = Color.rgb(215, 25, 32);
    private static final int BLACK = Color.rgb(16, 17, 20);
    private static final int WHITE = Color.WHITE;
    private static final int SURFACE = Color.rgb(247, 247, 248);
    private static final int BORDER = Color.rgb(226, 226, 230);
    private static final int MUTED = Color.rgb(98, 99, 104);

    private final Map<String, JSONObject> screens = new HashMap<>();
    private final Map<String, Integer> visibleItemCounts = new HashMap<>();
    private final Map<String, String> selectedCategories = new HashMap<>();
    private final Map<String, String> selectedOfferSections = new HashMap<>();
    private final Map<String, String> selectedPrograms = new HashMap<>();
    private final Map<String, String> selectedMessageDepartments = new HashMap<>();
    private final Map<String, String> searchQueries = new HashMap<>();
    private final Map<String, String> selectedFilters = new HashMap<>();
    private final Map<String, Boolean> expandedFilterSections = new HashMap<>();
    private final Map<String, Boolean> expandedAccountSections = new HashMap<>();
    private ConfigDatabase configDatabase;
    private JSONObject config;
    private ScrollView scrollView;
    private LinearLayout content;
    private LinearLayout nav;
    private Button cartButton;
    private String currentScreen = "home";
    private JSONObject activeProgramDetail;
    private int cartCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(BLACK);
        getWindow().setNavigationBarColor(WHITE);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        loadConfig();
        setContentView(buildShell());
        renderScreen(currentScreen);
        checkForConfigUpdate();
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
        String manifestUrl = config.optJSONObject("remoteConfig") == null
                ? ""
                : config.optJSONObject("remoteConfig").optString("manifestUrl", "");
        if (manifestUrl.trim().isEmpty()) {
            return;
        }

        new Thread(() -> {
            try {
                JSONObject manifest = new JSONObject(downloadText(manifestUrl));
                validateManifest(manifest);
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
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "خطا در به‌روزرسانی تنظیمات", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception ignored) {
            }
        }).start();
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

            String url = screenMeta.optString("url", "");
            if (url.trim().isEmpty()) {
                continue;
            }

            String screenJson = downloadText(url);
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

    @SuppressWarnings("deprecation")
    private View buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(WHITE);
        root.setLayoutParams(match());
        root.setPadding(dp(0), dp(0), dp(0), dp(0));

        View topBar = buildTopBar();
        int topBarBasePaddingLeft = topBar.getPaddingLeft();
        int topBarBasePaddingTop = topBar.getPaddingTop();
        int topBarBasePaddingRight = topBar.getPaddingRight();
        int topBarBasePaddingBottom = topBar.getPaddingBottom();
        root.addView(topBar);

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int contentBottomPadding = dp(112);
        content.setPadding(dp(16), dp(12), dp(16), contentBottomPadding);
        scrollView.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        int navBaseHeight = dp(72);
        int navBottomPadding = dp(8);
        nav.setPadding(dp(8), dp(8), dp(8), navBottomPadding);
        nav.setBackground(rounded(WHITE, 0, BORDER, 1));
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
            content.setPadding(dp(16), dp(12), dp(16), contentBottomPadding + systemBottomInset);
            nav.setPadding(dp(8), dp(8), dp(8), navBottomPadding + systemBottomInset);

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
        bar.setPadding(dp(16), dp(12), dp(16), dp(10));
        bar.setBackgroundColor(BLACK);

        TextView logo = text("EtokBike", 22, WHITE, true);
        bar.addView(logo, new LinearLayout.LayoutParams(0, -2, 1));

        Button messages = topIconButton("۲", R.drawable.ic_message_24);
        messages.setOnClickListener(v -> openScreen("messages"));
        bar.addView(messages, new LinearLayout.LayoutParams(dp(58), dp(44)));

        View actionSpacer = new View(this);
        bar.addView(actionSpacer, new LinearLayout.LayoutParams(dp(8), 1));

        cartButton = topIconButton("", R.drawable.ic_cart_24);
        updateCartButton();
        cartButton.setOnClickListener(v -> openScreen("cart"));
        LinearLayout.LayoutParams cartParams = new LinearLayout.LayoutParams(dp(58), dp(44));
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
                boolean selected = screen.equals(currentScreen);
                TextView tab = text(label, 13, selected ? RED : BLACK, selected);
                tab.setGravity(Gravity.CENTER);
                tab.setSingleLine(true);
                tab.setEllipsize(TextUtils.TruncateAt.END);
                tab.setBackground(selected ? rounded(SURFACE, 20, 0, 0) : rounded(WHITE, 20, 0, 0));
                tab.setOnClickListener(v -> openScreen(screen));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
                params.setMargins(dp(2), dp(0), dp(2), dp(0));
                nav.addView(tab, params);
            }
        } catch (Exception e) {
            Toast.makeText(this, "خطا در نمایش ناوبری", Toast.LENGTH_SHORT).show();
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
        if ("program-detail".equals(screenId)) {
            renderProgramDetailScreen();
            return;
        }

        JSONObject screen = screens.get(screenId);
        if (screen == null) return;

        currentScreen = screenId;
        content.removeAllViews();
        renderNavigation();

        try {
            if (!screen.optBoolean("hideTitle", false)) {
                TextView title = text(screen.getString("title"), 28, BLACK, true);
                title.setGravity(Gravity.RIGHT);
                content.addView(title, new LinearLayout.LayoutParams(-1, -2));
                addSpace(content, 14);
            }

            JSONArray sections = screen.getJSONArray("sections");
            for (int i = 0; i < sections.length(); i++) {
                renderSection(sections.getJSONObject(i));
                addSpace(content, 14);
            }
            if (resetScroll) {
                scrollToTop();
            }
        } catch (Exception e) {
            Toast.makeText(this, "خطا در نمایش صفحه", Toast.LENGTH_SHORT).show();
        }
    }

    private void renderProgramDetailScreen() {
        if (activeProgramDetail == null) {
            renderScreen("events");
            return;
        }

        currentScreen = "program-detail";
        content.removeAllViews();
        renderNavigation();

        try {
            Button back = button("بازگشت به برنامه‌ها", false);
            back.setOnClickListener(v -> renderScreen("events"));
            content.addView(back, new LinearLayout.LayoutParams(-1, dp(44)));
            addSpace(content, 12);

            TextView title = text(activeProgramDetail.getString("title"), 28, BLACK, true);
            title.setGravity(Gravity.RIGHT);
            content.addView(title, new LinearLayout.LayoutParams(-1, -2));
            addSpace(content, 14);

            content.addView(programDetail(activeProgramDetail));
            if (isFinishedProgram(activeProgramDetail) && activeProgramDetail.optJSONArray("gallery") != null && activeProgramDetail.optJSONArray("gallery").length() > 0) {
                addSpace(content, 14);
                content.addView(sectionTitle(activeProgramDetail.optString("galleryTitle", "گالری برنامه")));
                content.addView(gallery(activeProgramDetail.getJSONArray("gallery")));
            }
            scrollToTop();
        } catch (Exception e) {
            Toast.makeText(this, "خطا در نمایش برنامه", Toast.LENGTH_SHORT).show();
        }
    }

    private void scrollToTop() {
        if (scrollView != null) {
            scrollView.post(() -> scrollView.scrollTo(0, 0));
        }
    }

    private void renderSection(JSONObject section) throws Exception {
        String type = section.getString("type");
        JSONObject data = sectionData(section);
        JSONObject layout = section.optJSONObject("layout");
        String presentation = layout == null ? "" : layout.optString("presentation", layout.optString("content", ""));
        if ("hero".equals(type)) {
            content.addView(hero(data));
        } else if ("category_grid".equals(type)) {
            content.addView(sectionTitle(data.getString("title")));
            content.addView(grid(data.getJSONArray("items"), true));
        } else if ("product_row".equals(type)) {
            content.addView(sectionTitle(data.getString("title")));
            content.addView(horizontalCards(data.getJSONArray("items")));
        } else if ("offer_sections".equals(type)) {
            content.addView(offerSections(data));
        } else if ("program_sections".equals(type)) {
            content.addView(programSections(section));
        } else if ("product_list".equals(type)) {
            content.addView(sectionTitle(data.getString("title")));
            content.addView(productList(data));
        } else if ("service_list".equals(type) || "schedule_list".equals(type) || "activity_list".equals(type)) {
            content.addView(sectionTitle(data.getString("title")));
            if ("carousel".equals(presentation)) {
                content.addView(horizontalCards(data.getJSONArray("items")));
            } else {
                content.addView(listCards(data.getJSONArray("items")));
            }
        } else if ("client_details".equals(type)) {
            content.addView(sectionTitle(data.getString("title")));
            content.addView(clientDetails(data));
        } else if ("purchase_history".equals(type)) {
            content.addView(sectionTitle(data.getString("title")));
            content.addView(accountList(data.getJSONArray("items")));
        } else if ("ongoing_purchase".equals(type)) {
            content.addView(sectionTitle(data.getString("title")));
            content.addView(ongoingPurchases(section));
        } else if ("message_center".equals(type)) {
            content.addView(messageCenter(section));
        } else if ("cart_summary".equals(type)) {
            content.addView(cartSummary(data));
        } else if ("service_booking_form".equals(type)) {
            content.addView(sectionTitle(data.getString("title")));
            content.addView(serviceBookingForm(data));
        } else if ("status_tracker".equals(type)) {
            content.addView(sectionTitle(data.getString("title")));
            content.addView(statusTrackers(section));
        } else if ("bike_profile_list".equals(type)) {
            content.addView(sectionTitle(data.getString("title")));
            content.addView(bikeProfiles(data.getJSONArray("items")));
        } else if ("business_info".equals(type)) {
            content.addView(sectionTitle(data.getString("title")));
            content.addView(businessInfo(data.getJSONArray("items")));
        } else if ("checkout_note".equals(type) || "profile_summary".equals(type)) {
            content.addView(infoPanel(data));
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

        LinearLayout box = panel(BLACK);
        box.setPadding(dp(16), dp(16), dp(16), dp(16));
        box.addView(text(section.getString("title"), 22, WHITE, true), new LinearLayout.LayoutParams(-1, -2));
        addSpace(box, 6);
        box.addView(text(section.getString("subtitle"), 14, Color.rgb(230, 230, 232), false), new LinearLayout.LayoutParams(-1, -2));
        addSpace(box, 12);
        Button action = button(section.getString("actionLabel"), true);
        String target = section.optString("target", "shop");
        action.setOnClickListener(v -> openScreen(target));
        box.addView(action, new LinearLayout.LayoutParams(-1, dp(44)));
        return box;
    }

    private View bikeShopHero(JSONObject section) throws Exception {
        LinearLayout box = panel(Color.rgb(18, 19, 23));
        box.setPadding(dp(16), dp(16), dp(16), dp(16));

        String eyebrow = section.optString("eyebrow", "");
        if (!eyebrow.isEmpty()) {
            TextView chip = pillText(eyebrow, 12, WHITE, Color.rgb(72, 24, 27), RED);
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(-2, dp(34));
            chipParams.setMargins(dp(0), dp(0), dp(0), dp(10));
            box.addView(chip, chipParams);
        }

        TextView title = text(section.getString("title"), 25, WHITE, true);
        title.setMaxLines(3);
        box.addView(title, new LinearLayout.LayoutParams(-1, -2));
        addSpace(box, 7);

        TextView subtitle = text(section.getString("subtitle"), 14, Color.rgb(224, 225, 229), false);
        subtitle.setMaxLines(3);
        box.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

        addSpace(box, 14);
        ImageView bike = new ImageView(this);
        bike.setImageResource(heroVisualResource(section.optString("visual", "bike")));
        bike.setAdjustViewBounds(true);
        bike.setScaleType(ImageView.ScaleType.FIT_CENTER);
        bike.setBackground(rounded(Color.rgb(29, 30, 35), 8, Color.rgb(58, 60, 66), 1));
        bike.setPadding(dp(8), dp(8), dp(8), dp(8));
        box.addView(bike, new LinearLayout.LayoutParams(-1, dp(150)));

        String featureTitle = section.optString("featureTitle", "");
        if (!featureTitle.isEmpty()) {
            addSpace(box, 12);
            LinearLayout feature = new LinearLayout(this);
            feature.setOrientation(LinearLayout.VERTICAL);
            feature.setGravity(Gravity.RIGHT);
            feature.setPadding(dp(12), dp(12), dp(12), dp(12));
            feature.setBackground(rounded(Color.rgb(31, 32, 37), 8, Color.rgb(70, 72, 78), 1));
            feature.addView(text(featureTitle, 16, WHITE, true), new LinearLayout.LayoutParams(-1, -2));
            String featureSubtitle = section.optString("featureSubtitle", "");
            if (!featureSubtitle.isEmpty()) {
                addSpace(feature, 4);
                feature.addView(text(featureSubtitle, 12, Color.rgb(205, 207, 213), false), new LinearLayout.LayoutParams(-1, -2));
            }
            String featurePrice = section.optString("featurePrice", "");
            if (!featurePrice.isEmpty()) {
                addSpace(feature, 7);
                feature.addView(text(featurePrice, 14, Color.rgb(255, 208, 103), true), new LinearLayout.LayoutParams(-1, -2));
            }
            box.addView(feature, new LinearLayout.LayoutParams(-1, -2));
        }

        JSONArray stats = section.optJSONArray("stats");
        if (stats != null && stats.length() > 0) {
            addSpace(box, 12);
            box.addView(heroStats(stats), new LinearLayout.LayoutParams(-1, -2));
        }

        addSpace(box, 14);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button primary = button(section.optString("primaryActionLabel", section.optString("actionLabel", "مشاهده فروشگاه")), true);
        primary.setOnClickListener(v -> openScreen(section.optString("primaryTarget", section.optString("target", "shop"))));
        Button secondary = button(section.optString("secondaryActionLabel", "رزرو سرویس"), false);
        secondary.setTextColor(WHITE);
        secondary.setBackground(rounded(Color.rgb(35, 36, 41), 8, Color.rgb(92, 94, 102), 1));
        secondary.setOnClickListener(v -> openScreen(section.optString("secondaryTarget", "services")));
        LinearLayout.LayoutParams primaryParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        primaryParams.setMargins(dp(4), dp(0), dp(0), dp(0));
        LinearLayout.LayoutParams secondaryParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        secondaryParams.setMargins(dp(0), dp(0), dp(4), dp(0));
        actions.addView(primary, primaryParams);
        actions.addView(secondary, secondaryParams);
        box.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }

    private View heroStats(JSONArray stats) throws Exception {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < stats.length(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            for (int j = 0; j < 2 && i + j < stats.length(); j++) {
                JSONObject item = stats.getJSONObject(i + j);
                LinearLayout stat = new LinearLayout(this);
                stat.setOrientation(LinearLayout.VERTICAL);
                stat.setGravity(Gravity.RIGHT);
                stat.setPadding(dp(10), dp(9), dp(10), dp(9));
                stat.setBackground(rounded(Color.rgb(27, 28, 33), 8, Color.rgb(55, 57, 64), 1));
                stat.addView(text(item.getString("value"), 15, WHITE, true), new LinearLayout.LayoutParams(-1, -2));
                addSpace(stat, 2);
                TextView label = text(item.getString("label"), 11, Color.rgb(187, 189, 196), false);
                label.setMaxLines(2);
                stat.addView(label, new LinearLayout.LayoutParams(-1, -2));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
                params.setMargins(dp(3), dp(3), dp(3), dp(3));
                row.addView(stat, params);
            }
            wrap.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        return wrap;
    }

    private int heroVisualResource(String visual) {
        if ("service".equals(visual)) return R.drawable.hero_service_shop;
        if ("events".equals(visual)) return R.drawable.hero_events_shop;
        if ("account".equals(visual)) return R.drawable.hero_account_shop;
        if ("messages".equals(visual)) return R.drawable.hero_messages_shop;
        if ("cart".equals(visual)) return R.drawable.hero_cart_shop;
        return R.drawable.hero_bike_shop;
    }

    private View grid(JSONArray items, boolean navigable) throws Exception {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < items.length(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            for (int j = 0; j < 2 && i + j < items.length(); j++) {
                JSONObject item = items.getJSONObject(i + j);
                View card = smallCard(item, navigable);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(112), 1);
                params.setMargins(dp(4), dp(4), dp(4), dp(4));
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
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(250), dp(230));
            params.setMargins(dp(6), dp(4), dp(6), dp(4));
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
            tab.setBackground(rounded(active ? RED : SURFACE, 22, active ? RED : BORDER, 1));
            tab.setOnClickListener(v -> {
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
        wrap.addView(programCards(items, key));

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

    private View programCards(List<JSONObject> items, String key) throws Exception {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < items.size(); i++) {
            View card = programCard(items.get(i), key);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(dp(0), dp(5), dp(0), dp(7));
            list.addView(card, params);
        }
        return list;
    }

    private View programCard(JSONObject item, String key) throws Exception {
        LinearLayout card = panel(WHITE);
        card.setBackgroundColor(WHITE);
        String id = item.getString("id");
        card.setOnClickListener(v -> {
            selectedPrograms.put(key, id);
            activeProgramDetail = item;
            renderScreen("program-detail");
        });

        card.addView(thumbnail(item, dp(92)), new LinearLayout.LayoutParams(-1, dp(92)));
        addSpace(card, 9);
        TextView title = text(item.getString("title"), 16, BLACK, true);
        title.setMaxLines(2);
        card.addView(title, new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 4);
        card.addView(text(item.getString("dateLabel"), 13, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 4);
        card.addView(text(item.optString("statusLabel", ""), 13, isFutureProgram(item) ? RED : MUTED, true), new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 8);
        Button action = button(isFutureProgram(item) ? item.optString("bookLabel", "رزرو برنامه") : item.optString("viewLabel", "مشاهده برنامه"), false);
        action.setOnClickListener(v -> {
            selectedPrograms.put(key, id);
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
        card.addView(text(item.optString("advertisement", item.optString("description", "")), 14, Color.rgb(70, 71, 76), false), new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 9);
        card.addView(text(item.getString("dateLabel") + " · " + item.optString("statusLabel", ""), 13, isFutureProgram(item) ? RED : MUTED, true), new LinearLayout.LayoutParams(-1, -2));

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
            book.setOnClickListener(v -> Toast.makeText(this, "درخواست رزرو برنامه ثبت شد", Toast.LENGTH_SHORT).show());
            card.addView(book, new LinearLayout.LayoutParams(-1, dp(44)));
        }
        return card;
    }

    private View gallery(JSONArray photos) throws Exception {
        LinearLayout gallery = new LinearLayout(this);
        gallery.setOrientation(LinearLayout.VERTICAL);

        JSONObject featured = photos.getJSONObject(0);
        gallery.addView(galleryPhoto(featured, true), new LinearLayout.LayoutParams(-1, dp(220)));

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
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(150), 1);
                params.setMargins(dp(4), dp(4), dp(4), dp(4));
                row.addView(tile, params);
            }
            grid.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        gallery.addView(grid);
        return gallery;
    }

    private View galleryPhoto(JSONObject photo, boolean featured) throws Exception {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.BOTTOM | Gravity.RIGHT);
        tile.setPadding(dp(12), dp(12), dp(12), dp(12));
        tile.setBackground(rounded(photo.optString("thumbnailColor", "#101114"), 10, 0, 0));

        TextView marker = text(photo.optString("thumbnailText", "PHOTO"), featured ? 28 : 18, WHITE, true);
        marker.setGravity(Gravity.RIGHT);
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

    private View compactGallery(JSONArray photos) throws Exception {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < photos.length(); i++) {
            JSONObject photo = photos.getJSONObject(i);
            LinearLayout item = panel(WHITE);
            item.addView(thumbnail(photo, dp(112)), new LinearLayout.LayoutParams(-1, dp(112)));
            addSpace(item, 6);
            TextView caption = text(photo.optString("caption", ""), 12, MUTED, false);
            caption.setMaxLines(2);
            caption.setEllipsize(TextUtils.TruncateAt.END);
            item.addView(caption, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(160), dp(190));
            params.setMargins(dp(6), dp(4), dp(6), dp(4));
            row.addView(item, params);
        }
        scroll.addView(row);
        return scroll;
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
            card.setOnClickListener(v -> {
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
                card.addView(text(unread, 12, RED, true), new LinearLayout.LayoutParams(-1, -2));
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(dp(0), dp(4), dp(0), dp(6));
            list.addView(card, params);
        }
        return list;
    }

    private View cartSummary(JSONObject data) throws Exception {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(sectionTitle(data.getString("title")));

        JSONArray items = data.getJSONArray("items");
        for (int i = 0; i < items.length(); i++) {
            LinearLayout card = panel(WHITE);
            card.addView(accountCard(items.getJSONObject(i)), new LinearLayout.LayoutParams(-1, -2));
            addSpace(card, 8);
            LinearLayout controls = new LinearLayout(this);
            controls.setOrientation(LinearLayout.HORIZONTAL);
            Button remove = button("حذف", false);
            Button minus = button("-", false);
            Button plus = button("+", false);
            remove.setOnClickListener(v -> Toast.makeText(this, "آیتم از سبد نمونه حذف شد", Toast.LENGTH_SHORT).show());
            minus.setOnClickListener(v -> Toast.makeText(this, "تعداد کاهش یافت", Toast.LENGTH_SHORT).show());
            plus.setOnClickListener(v -> Toast.makeText(this, "تعداد افزایش یافت", Toast.LENGTH_SHORT).show());
            controls.addView(remove, new LinearLayout.LayoutParams(0, dp(40), 1));
            controls.addView(minus, new LinearLayout.LayoutParams(0, dp(40), 1));
            controls.addView(plus, new LinearLayout.LayoutParams(0, dp(40), 1));
            card.addView(controls, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(dp(0), dp(5), dp(0), dp(7));
            wrap.addView(card, params);
        }

        LinearLayout total = panel(SURFACE);
        total.addView(text(data.getString("totalLabel"), 13, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        addSpace(total, 4);
        total.addView(text(data.getString("total"), 20, BLACK, true), new LinearLayout.LayoutParams(-1, -2));
        addSpace(total, 10);
        Button checkout = button(data.optString("checkoutLabel", "ثبت سفارش"), true);
        checkout.setOnClickListener(v -> Toast.makeText(this, "سفارش نمونه ثبت شد", Toast.LENGTH_SHORT).show());
        total.addView(checkout, new LinearLayout.LayoutParams(-1, dp(46)));
        wrap.addView(total, new LinearLayout.LayoutParams(-1, -2));
        return wrap;
    }

    private View serviceBookingForm(JSONObject data) throws Exception {
        LinearLayout card = panel(WHITE);
        card.addView(dropdownField(data.getString("serviceLabel"), data.getJSONArray("services")), new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 10);
        card.addView(dropdownField(data.getString("bikeLabel"), data.getJSONArray("bikes")), new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 10);
        card.addView(dropdownField(data.getString("timeLabel"), data.getJSONArray("timeSlots")), new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 10);

        EditText problem = new EditText(this);
        problem.setHint(data.optString("problemPlaceholder", "توضیح مشکل"));
        problem.setTextSize(14);
        problem.setTextColor(BLACK);
        problem.setGravity(Gravity.RIGHT);
        problem.setMinLines(3);
        problem.setBackground(rounded(SURFACE, 8, BORDER, 1));
        problem.setPadding(dp(12), dp(8), dp(12), dp(8));
        card.addView(problem, new LinearLayout.LayoutParams(-1, dp(104)));
        addSpace(card, 10);

        Button submit = button(data.optString("submitLabel", "ثبت درخواست سرویس"), true);
        submit.setOnClickListener(v -> Toast.makeText(this, "درخواست سرویس ثبت شد", Toast.LENGTH_SHORT).show());
        card.addView(submit, new LinearLayout.LayoutParams(-1, dp(46)));
        return card;
    }

    private View dropdownField(String label, JSONArray options) throws Exception {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.RIGHT);
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
        return box;
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
            LinearLayout bubble = panel("client".equals(message.optString("sender", "")) ? WHITE : Color.rgb(236, 236, 238));
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
        input.setHint(department.optString("placeholder", "پیام خود را بنویسید"));
        input.setGravity(Gravity.RIGHT);
        input.setMinLines(3);
        input.setBackground(rounded(SURFACE, 8, BORDER, 1));
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        card.addView(input, new LinearLayout.LayoutParams(-1, dp(108)));
        addSpace(card, 10);

        Button send = button(department.optString("sendLabel", "ارسال پیام"), true);
        send.setOnClickListener(v -> {
            input.setText("");
            Toast.makeText(this, "پیام برای " + department.optString("title", "واحد پشتیبانی") + " ثبت شد", Toast.LENGTH_SHORT).show();
        });
        card.addView(send, new LinearLayout.LayoutParams(-1, dp(44)));
        return card;
    }

    private View productList(JSONObject section) throws Exception {
        String key = currentScreen + ":" + section.optString("title", "products");
        String selectedCategory = selectedCategories.containsKey(key)
                ? selectedCategories.get(key)
                : section.optString("defaultCategory", "");
        Map<String, String> filters = currentFilterValues(section, key);
        String query = searchQueries.containsKey(key) ? searchQueries.get(key) : "";
        List<JSONObject> items = filteredProducts(section.getJSONArray("items"), selectedCategory, filters, query);
        int initialItems = section.optInt("initialItems", 4);
        int pageSize = section.optInt("pageSize", initialItems);
        int visible = visibleItemCounts.containsKey(key) ? visibleItemCounts.get(key) : initialItems;
        visible = Math.min(visible, items.size());

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.addView(searchPanel(section, key, query), new LinearLayout.LayoutParams(-1, -2));
        addSpace(list, 10);
        if (section.has("categories")) {
            list.addView(categoryDropdown(section, key, selectedCategory), new LinearLayout.LayoutParams(-1, -2));
            addSpace(list, 10);
        }
        if (section.has("filters")) {
            list.addView(filtersPanel(section, key, filters), new LinearLayout.LayoutParams(-1, -2));
            addSpace(list, 10);
        }
        for (int i = 0; i < visible; i++) {
            View card = productCard(items.get(i), true);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(dp(0), dp(5), dp(0), dp(7));
            list.addView(card, params);
        }

        if (visible == 0) {
            list.addView(infoText(section.optString("emptyStateText", "")));
        }

        if (visible < items.size()) {
            Button more = button(section.optString("loadMoreLabel", "نمایش بیشتر"), false);
            int nextVisible = Math.min(visible + pageSize, items.size());
            more.setText(more.getText() + " (" + visible + "/" + items.size() + ")");
            more.setOnClickListener(v -> {
                visibleItemCounts.put(key, nextVisible);
                renderScreen(currentScreen);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(46));
            params.setMargins(dp(0), dp(4), dp(0), dp(4));
            list.addView(more, params);
        }
        return list;
    }

    private View searchPanel(JSONObject section, String key, String query) {
        LinearLayout box = panel(SURFACE);
        box.addView(text(section.optString("searchLabel", "جستجو"), 13, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        addSpace(box, 6);
        EditText input = new EditText(this);
        input.setText(query);
        input.setHint(section.optString("searchPlaceholder", "نام محصول، قطعه یا لوازم را بنویسید"));
        input.setTextSize(14);
        input.setTextColor(BLACK);
        input.setSingleLine(true);
        input.setGravity(Gravity.RIGHT);
        input.setBackground(rounded(WHITE, 8, BORDER, 1));
        input.setPadding(dp(12), dp(0), dp(12), dp(0));
        box.addView(input, new LinearLayout.LayoutParams(-1, dp(46)));
        addSpace(box, 8);
        Button apply = button("اعمال جستجو", false);
        apply.setOnClickListener(v -> {
            searchQueries.put(key, input.getText().toString().trim());
            visibleItemCounts.remove(key);
            renderScreen(currentScreen);
        });
        box.addView(apply, new LinearLayout.LayoutParams(-1, dp(42)));
        return box;
    }

    private List<JSONObject> filteredProducts(JSONArray source, String selectedCategory, Map<String, String> filters, String query) throws Exception {
        List<JSONObject> result = new ArrayList<>();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase();
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.getJSONObject(i);
            String searchable = (item.optString("title", "") + " " + item.optString("subtitle", "") + " " + item.optString("description", "")).toLowerCase();
            boolean matchesSearch = normalizedQuery.isEmpty() || searchable.contains(normalizedQuery);
            if ((selectedCategory.isEmpty() || selectedCategory.equals(item.optString("category", ""))) && matchesFilters(item, filters) && matchesSearch) {
                result.add(item);
            }
        }
        sortProducts(result, filters.get("sort"));
        return result;
    }

    private boolean matchesFilters(JSONObject item, Map<String, String> filters) {
        String availability = filters.containsKey("availability") ? filters.get("availability") : "all";
        if (!"all".equals(availability) && !availability.equals(item.optString("availability", ""))) {
            return false;
        }

        String priceBand = filters.containsKey("priceBand") ? filters.get("priceBand") : "all";
        int price = item.optInt("priceValue", 0);
        if ("under_2m".equals(priceBand)) return price < 2000000;
        if ("2m_20m".equals(priceBand)) return price >= 2000000 && price <= 20000000;
        if ("over_20m".equals(priceBand)) return price > 20000000;
        return true;
    }

    private void sortProducts(List<JSONObject> items, String sort) {
        if ("price_low".equals(sort)) {
            Collections.sort(items, (left, right) -> left.optInt("priceValue", 0) - right.optInt("priceValue", 0));
        } else if ("price_high".equals(sort)) {
            Collections.sort(items, (left, right) -> right.optInt("priceValue", 0) - left.optInt("priceValue", 0));
        }
    }

    private Map<String, String> currentFilterValues(JSONObject section, String key) throws Exception {
        Map<String, String> values = new HashMap<>();
        if (!section.has("filters")) return values;

        JSONArray filters = section.getJSONArray("filters");
        for (int i = 0; i < filters.length(); i++) {
            JSONObject filter = filters.getJSONObject(i);
            String id = filter.getString("id");
            String stateKey = key + ":" + id;
            values.put(id, selectedFilters.containsKey(stateKey) ? selectedFilters.get(stateKey) : filter.optString("default", "all"));
        }
        return values;
    }

    private View categoryDropdown(JSONObject section, String key, String selectedCategory) throws Exception {
        LinearLayout box = panel(SURFACE);
        box.addView(text(section.optString("categoryLabel", "دسته‌بندی"), 13, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        addSpace(box, 6);

        JSONArray categories = section.getJSONArray("categories");
        List<String> labels = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        int selectedIndex = 0;
        for (int i = 0; i < categories.length(); i++) {
            JSONObject category = categories.getJSONObject(i);
            ids.add(category.getString("id"));
            labels.add(category.getString("label"));
            if (category.getString("id").equals(selectedCategory)) {
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
                String newCategory = ids.get(position);
                if (!newCategory.equals(selectedCategory)) {
                    selectedCategories.put(key, newCategory);
                    visibleItemCounts.remove(key);
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

    private View filtersPanel(JSONObject section, String key, Map<String, String> filtersState) throws Exception {
        LinearLayout box = panel(SURFACE);
        boolean expanded = expandedFilterSections.containsKey(key) && expandedFilterSections.get(key);

        Button toggle = button(expanded
                ? section.optString("filtersExpandedLabel", section.optString("filtersTitle", ""))
                : section.optString("filtersCollapsedLabel", section.optString("filtersTitle", "")), false);
        toggle.setTextColor(BLACK);
        toggle.setOnClickListener(v -> {
            expandedFilterSections.put(key, !expanded);
            renderScreen(currentScreen);
        });
        box.addView(toggle, new LinearLayout.LayoutParams(-1, dp(46)));

        if (!expanded) {
            return box;
        }

        addSpace(box, 8);

        JSONArray filters = section.getJSONArray("filters");
        for (int i = 0; i < filters.length(); i++) {
            JSONObject filter = filters.getJSONObject(i);
            box.addView(filterDropdown(filter, key, filtersState.get(filter.getString("id"))), new LinearLayout.LayoutParams(-1, -2));
            if (i < filters.length() - 1) addSpace(box, 8);
        }

        Button reset = button(section.optString("resetFiltersLabel", ""), false);
        reset.setOnClickListener(v -> {
            try {
                JSONArray filterList = section.getJSONArray("filters");
                for (int i = 0; i < filterList.length(); i++) {
                    selectedFilters.remove(key + ":" + filterList.getJSONObject(i).getString("id"));
                }
                visibleItemCounts.remove(key);
                renderScreen(currentScreen);
            } catch (Exception e) {
                Toast.makeText(this, "خطا در حذف فیلترها", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(-1, dp(44));
        resetParams.setMargins(dp(0), dp(10), dp(0), dp(0));
        box.addView(reset, resetParams);
        return box;
    }

    private View filterDropdown(JSONObject filter, String key, String selectedValue) throws Exception {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.RIGHT);

        box.addView(text(filter.getString("label"), 13, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        addSpace(box, 4);

        JSONArray options = filter.getJSONArray("options");
        List<String> labels = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        int selectedIndex = 0;
        for (int i = 0; i < options.length(); i++) {
            JSONObject option = options.getJSONObject(i);
            ids.add(option.getString("id"));
            labels.add(option.getString("label"));
            if (option.getString("id").equals(selectedValue)) {
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
                    String filterId = filter.getString("id");
                    String stateKey = key + ":" + filterId;
                    String newValue = ids.get(position);
                    if (!newValue.equals(selectedFilters.get(stateKey)) && !isDefaultFilter(filter, newValue)) {
                        selectedFilters.put(stateKey, newValue);
                        visibleItemCounts.remove(key);
                        renderScreen(currentScreen);
                    } else if (selectedFilters.containsKey(stateKey) && isDefaultFilter(filter, newValue)) {
                        selectedFilters.remove(stateKey);
                        visibleItemCounts.remove(key);
                        renderScreen(currentScreen);
                    }
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "خطا در اعمال فیلتر", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        box.addView(spinner, new LinearLayout.LayoutParams(-1, dp(44)));
        return box;
    }

    private boolean isDefaultFilter(JSONObject filter, String value) {
        return value.equals(filter.optString("default", "all"));
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
            card.addView(text(description, 12, Color.rgb(70, 71, 76), false), new LinearLayout.LayoutParams(-1, -2));
        }
        addSpace(card, 8);
        card.addView(text(item.optString("price", ""), 14, RED, true), new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    private View ongoingPurchaseCard(JSONObject item, String key) throws Exception {
        LinearLayout card = panel(WHITE);
        card.addView(text(item.getString("title"), 17, BLACK, true), new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 5);
        card.addView(text(item.getString("subtitle"), 13, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 8);
        card.addView(text(item.optString("status", ""), 14, RED, true), new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 10);

        boolean expanded = expandedAccountSections.containsKey(key) && expandedAccountSections.get(key);
        Button toggle = button(expanded ? item.optString("collapseLabel", "بستن وضعیت") : item.optString("expandLabel", "مشاهده وضعیت"), false);
        toggle.setOnClickListener(v -> {
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

    private View smallCard(JSONObject item, boolean navigable) throws Exception {
        LinearLayout card = panel(SURFACE);
        String badge = item.optString("badge", "");
        if (!badge.isEmpty()) {
            TextView badgeView = pillText(badge, 11, RED, Color.rgb(255, 235, 236), RED);
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(-2, dp(28));
            badgeParams.setMargins(dp(0), dp(0), dp(0), dp(8));
            card.addView(badgeView, badgeParams);
        }
        card.addView(text(item.getString("title"), 16, BLACK, true), new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 6);
        card.addView(text(item.getString("subtitle"), 12, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        if (navigable) {
            String target = item.optString("target", "shop");
            card.setOnClickListener(v -> openScreen(target));
        }
        return card;
    }

    private View productCard(JSONObject item, boolean compact) throws Exception {
        LinearLayout card = panel(WHITE);
        card.setBackgroundColor(WHITE);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(compact ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        body.setGravity(Gravity.RIGHT);

        View thumbnail = thumbnail(item, compact ? dp(92) : dp(126));
        LinearLayout.LayoutParams thumbParams = compact
                ? new LinearLayout.LayoutParams(dp(92), dp(92))
                : new LinearLayout.LayoutParams(-1, dp(126));
        thumbParams.setMargins(compact ? dp(12) : 0, 0, 0, compact ? 0 : dp(10));
        body.addView(thumbnail, thumbParams);

        LinearLayout detail = new LinearLayout(this);
        detail.setOrientation(LinearLayout.VERTICAL);
        detail.setGravity(Gravity.RIGHT);
        TextView title = text(item.getString("title"), 17, BLACK, true);
        title.setMaxLines(2);
        detail.addView(title, new LinearLayout.LayoutParams(-1, -2));
        addSpace(detail, 5);
        detail.addView(text(item.getString("subtitle"), 13, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        String description = item.optString("description", "");
        if (!description.isEmpty()) {
            addSpace(detail, 5);
            TextView desc = text(description, 12, Color.rgb(70, 71, 76), false);
            desc.setMaxLines(compact ? 2 : 3);
            desc.setEllipsize(TextUtils.TruncateAt.END);
            detail.addView(desc, new LinearLayout.LayoutParams(-1, -2));
        }
        addSpace(detail, 8);
        detail.addView(text(item.optString("price", ""), 14, RED, true), new LinearLayout.LayoutParams(-1, -2));
        String stockLabel = item.optString("stockLabel", "");
        if (!stockLabel.isEmpty()) {
            addSpace(detail, 5);
            detail.addView(text(stockLabel, 12, MUTED, true), new LinearLayout.LayoutParams(-1, -2));
        }

        body.addView(detail, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(body, new LinearLayout.LayoutParams(-1, -2));

        addSpace(card, 12);
        Button action = button("افزودن / رزرو", false);
        action.setOnClickListener(v -> {
            cartCount++;
            updateCartButton();
            Toast.makeText(this, "به سبد نمونه اضافه شد", Toast.LENGTH_SHORT).show();
        });
        card.addView(action, new LinearLayout.LayoutParams(-1, dp(44)));
        return card;
    }

    private View thumbnail(JSONObject item, int height) {
        TextView view = text(item.optString("thumbnailText", "ETOK"), 18, WHITE, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setBackground(rounded(item.optString("thumbnailColor", "#101114"), 8, 0, 0));
        view.setMinHeight(height);
        return view;
    }

    private View infoPanel(JSONObject section) throws Exception {
        LinearLayout card = panel(SURFACE);
        card.addView(text(section.getString("title"), 18, BLACK, true), new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 7);
        card.addView(text(section.getString("subtitle"), 14, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    private TextView infoText(String value) {
        TextView view = text(value, 14, MUTED, false);
        view.setPadding(dp(4), dp(12), dp(4), dp(12));
        return view;
    }

    private TextView sectionTitle(String value) {
        TextView title = text(value, 20, BLACK, true);
        title.setPadding(dp(0), dp(6), dp(0), dp(8));
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
        box.setGravity(Gravity.RIGHT);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.setBackground(rounded(color, 8, BORDER, 1));
        return box;
    }

    private GradientDrawable rounded(String color, int radius, int strokeColor, int strokeWidth) {
        return rounded(Color.parseColor(color), radius, strokeColor, strokeWidth);
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

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.RIGHT);
        view.setIncludeFontPadding(true);
        view.setLineSpacing(dp(2), 1.0f);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private Button button(String label, boolean filled) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTextColor(filled ? WHITE : BLACK);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(12), dp(0), dp(12), dp(0));
        button.setBackground(rounded(filled ? RED : Color.rgb(238, 238, 240), 8, filled ? RED : BORDER, 1));
        button.setGravity(Gravity.CENTER);
        return button;
    }

    private Button topIconButton(String label, int iconResource) {
        Button action = button(label, false);
        action.setTextColor(WHITE);
        action.setTextSize(13);
        action.setBackground(rounded(Color.rgb(28, 29, 34), 12, Color.rgb(74, 76, 84), 1));
        action.setCompoundDrawablesWithIntrinsicBounds(iconResource, 0, 0, 0);
        action.setCompoundDrawablePadding(dp(4));
        return action;
    }

    private void updateCartButton() {
        if (cartButton != null) {
            cartButton.setText(persianDigits(cartCount));
        }
    }

    private String persianDigits(int value) {
        char[] digits = String.valueOf(value).toCharArray();
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

    private String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(value.getBytes("UTF-8"));
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
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        input.close();
        return output.toString("UTF-8");
    }

    private ViewGroup.LayoutParams match() {
        return new ViewGroup.LayoutParams(-1, -1);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
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
            Cursor cursor = db.rawQuery("SELECT raw_json FROM app_manifest WHERE id = 1", null);
            try {
                return cursor.moveToFirst() ? cursor.getString(0) : null;
            } finally {
                cursor.close();
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
            Cursor cursor = db.rawQuery("SELECT raw_json, version FROM screen_configs WHERE screen_id = ?", new String[]{screenId});
            try {
                if (!cursor.moveToFirst()) {
                    return null;
                }
                return new ScreenCacheEntry(cursor.getString(0), cursor.getInt(1));
            } finally {
                cursor.close();
            }
        }

        int getScreenVersion(String screenId) {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = db.rawQuery("SELECT version FROM screen_configs WHERE screen_id = ?", new String[]{screenId});
            try {
                return cursor.moveToFirst() ? cursor.getInt(0) : 0;
            } finally {
                cursor.close();
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
