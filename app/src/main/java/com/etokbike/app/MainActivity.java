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
import android.widget.HorizontalScrollView;
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
import java.util.Comparator;
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
    private final Map<String, String> selectedFilters = new HashMap<>();
    private final Map<String, Boolean> expandedFilterSections = new HashMap<>();
    private ConfigDatabase configDatabase;
    private JSONObject config;
    private LinearLayout content;
    private LinearLayout nav;
    private String currentScreen = "home";
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
        String cachedManifest = configDatabase.getManifestJson();
        if (cachedManifest != null) {
            try {
                JSONObject manifest = new JSONObject(cachedManifest);
                validateManifest(manifest);
                return manifest;
            } catch (Exception ignored) {
            }
        }

        JSONObject bundledManifest = new JSONObject(readAsset(BUNDLED_MANIFEST_PATH));
        validateManifest(bundledManifest);
        configDatabase.saveManifest(bundledManifest, bundledManifest.optInt("appVersion", 0));
        return bundledManifest;
    }

    private void applyManifest(JSONObject nextConfig) throws Exception {
        validateManifest(nextConfig);
        config = nextConfig;
    }

    private void loadScreensFromCache(JSONObject manifest) throws Exception {
        screens.clear();
        JSONObject manifestScreens = manifest.getJSONObject("screens");
        JSONArray ids = manifestScreens.names();
        if (ids == null) return;

        for (int i = 0; i < ids.length(); i++) {
            String screenId = ids.getString(i);
            JSONObject screenMeta = manifestScreens.getJSONObject(screenId);
            JSONObject screen = loadScreen(screenId, screenMeta);
            screens.put(screenId, screen);
        }
    }

    private JSONObject loadScreen(String screenId, JSONObject screenMeta) throws Exception {
        String cachedScreen = configDatabase.getScreenJson(screenId);
        int localVersion = configDatabase.getScreenVersion(screenId);
        int bundledVersion = screenMeta.optInt("version", 0);
        if (cachedScreen != null && localVersion >= bundledVersion) {
            try {
                JSONObject screen = new JSONObject(cachedScreen);
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
                runOnUiThread(() -> {
                    try {
                        applyManifest(manifest);
                        loadScreensFromCache(manifest);
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

    private View buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(WHITE);
        root.setLayoutParams(match());
        root.setPadding(dp(0), dp(0), dp(0), dp(0));

        root.addView(buildTopBar());

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(18));
        scrollView.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(8), dp(8), dp(8));
        nav.setBackgroundColor(WHITE);
        root.addView(nav, new LinearLayout.LayoutParams(-1, dp(72)));

        return root;
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(14), dp(16), dp(12));
        bar.setBackgroundColor(BLACK);

        TextView logo = text("EtokBike", 22, WHITE, true);
        bar.addView(logo, new LinearLayout.LayoutParams(0, -2, 1));

        Button cart = button("سبد خرید", true);
        cart.setOnClickListener(v -> Toast.makeText(this, "سبد خرید نمونه: " + cartCount + " مورد", Toast.LENGTH_SHORT).show());
        bar.addView(cart, new LinearLayout.LayoutParams(-2, dp(44)));
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
                tab.setBackgroundColor(WHITE);
                tab.setOnClickListener(v -> renderScreen(screen));
                nav.addView(tab, new LinearLayout.LayoutParams(0, -1, 1));
            }
        } catch (Exception e) {
            Toast.makeText(this, "خطا در نمایش ناوبری", Toast.LENGTH_SHORT).show();
        }
    }

    private void renderScreen(String screenId) {
        JSONObject screen = screens.get(screenId);
        if (screen == null) return;

        currentScreen = screenId;
        content.removeAllViews();
        renderNavigation();

        try {
            TextView title = text(screen.getString("title"), 28, BLACK, true);
            title.setGravity(Gravity.RIGHT);
            content.addView(title, new LinearLayout.LayoutParams(-1, -2));
            addSpace(content, 14);

            JSONArray sections = screen.getJSONArray("sections");
            for (int i = 0; i < sections.length(); i++) {
                renderSection(sections.getJSONObject(i));
                addSpace(content, 14);
            }
        } catch (Exception e) {
            Toast.makeText(this, "خطا در نمایش صفحه", Toast.LENGTH_SHORT).show();
        }
    }

    private void renderSection(JSONObject section) throws Exception {
        String type = section.getString("type");
        JSONObject data = sectionData(section);
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
        } else if ("product_list".equals(type)) {
            content.addView(sectionTitle(data.getString("title")));
            content.addView(productList(data));
        } else if ("service_list".equals(type) || "schedule_list".equals(type) || "activity_list".equals(type)) {
            content.addView(sectionTitle(data.getString("title")));
            content.addView(listCards(data.getJSONArray("items")));
        } else if ("checkout_note".equals(type) || "profile_summary".equals(type)) {
            content.addView(infoPanel(data));
        }
    }

    private JSONObject sectionData(JSONObject section) {
        JSONObject data = section.optJSONObject("data");
        return data == null ? section : data;
    }

    private View hero(JSONObject section) throws Exception {
        LinearLayout box = panel(BLACK);
        box.setPadding(dp(18), dp(20), dp(18), dp(20));
        box.addView(text(section.getString("title"), 24, WHITE, true), new LinearLayout.LayoutParams(-1, -2));
        addSpace(box, 8);
        box.addView(text(section.getString("subtitle"), 15, Color.rgb(230, 230, 232), false), new LinearLayout.LayoutParams(-1, -2));
        addSpace(box, 16);
        Button action = button(section.getString("actionLabel"), true);
        String target = section.optString("target", "shop");
        action.setOnClickListener(v -> renderScreen(target));
        box.addView(action, new LinearLayout.LayoutParams(-1, dp(48)));
        return box;
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

        wrap.addView(text(selectedSubsection.getString("title"), 16, BLACK, true), new LinearLayout.LayoutParams(-1, -2));
        addSpace(wrap, 6);
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

    private View productList(JSONObject section) throws Exception {
        String key = currentScreen + ":" + section.optString("title", "products");
        String selectedCategory = selectedCategories.containsKey(key)
                ? selectedCategories.get(key)
                : section.optString("defaultCategory", "");
        Map<String, String> filters = currentFilterValues(section, key);
        List<JSONObject> items = filteredProducts(section.getJSONArray("items"), selectedCategory, filters);
        int initialItems = section.optInt("initialItems", 4);
        int pageSize = section.optInt("pageSize", initialItems);
        int visible = visibleItemCounts.containsKey(key) ? visibleItemCounts.get(key) : initialItems;
        visible = Math.min(visible, items.size());

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
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

    private List<JSONObject> filteredProducts(JSONArray source, String selectedCategory, Map<String, String> filters) throws Exception {
        List<JSONObject> result = new ArrayList<>();
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.getJSONObject(i);
            if ((selectedCategory.isEmpty() || selectedCategory.equals(item.optString("category", ""))) && matchesFilters(item, filters)) {
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
            Collections.sort(items, Comparator.comparingInt(item -> item.optInt("priceValue", 0)));
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
        spinner.setSelection(selectedIndex);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String newCategory = ids.get(position);
                if (!newCategory.equals(selectedCategories.get(key))) {
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
        spinner.setSelection(selectedIndex);
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

    private View smallCard(JSONObject item, boolean navigable) throws Exception {
        LinearLayout card = panel(SURFACE);
        card.addView(text(item.getString("title"), 16, BLACK, true), new LinearLayout.LayoutParams(-1, -2));
        addSpace(card, 6);
        card.addView(text(item.getString("subtitle"), 12, MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        if (navigable) {
            String target = item.optString("target", "shop");
            card.setOnClickListener(v -> renderScreen(target));
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

        body.addView(detail, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(body, new LinearLayout.LayoutParams(-1, -2));

        addSpace(card, 12);
        Button action = button("افزودن / رزرو", false);
        action.setOnClickListener(v -> {
            cartCount++;
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
        button.setBackgroundColor(filled ? RED : Color.rgb(238, 238, 240));
        button.setGravity(Gravity.CENTER);
        return button;
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

        String getScreenJson(String screenId) {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = db.rawQuery("SELECT raw_json FROM screen_configs WHERE screen_id = ?", new String[]{screenId});
            try {
                return cursor.moveToFirst() ? cursor.getString(0) : null;
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
    }
}
