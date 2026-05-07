package com.wltest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.animation.ValueAnimator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.wltest.probe.CaBundleInstaller;
import com.wltest.probe.NativeCurlBridge;

import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "wltest_checkers";
    private static final String PREF_RU = "ru_checkers";
    private static final String PREF_NON_RU = "non_ru_checkers";
    private static final String DEFAULT_RU = "ya.ru\nozon.ru\nmail.ru";
    private static final String DEFAULT_NON_RU = "google.com\nwhatismyip.com\nhttps://api.ipify.org/";

    private static final int CELLULAR_WAIT_SECONDS = 15;
    private static final int REQUEST_TIMEOUT_MS = 10000;

    private static final int COLOR_BG = Color.rgb(12, 16, 24);
    private static final int COLOR_PANEL = Color.rgb(22, 28, 40);
    private static final int COLOR_TEXT = Color.rgb(235, 240, 248);
    private static final int COLOR_MUTED = Color.rgb(143, 154, 174);
    private static final int COLOR_ACCENT = Color.rgb(72, 180, 255);
    private static final int COLOR_OK = Color.rgb(86, 214, 130);
    private static final int COLOR_ERR = Color.rgb(255, 104, 104);
    private static final int COLOR_WARN = Color.rgb(255, 196, 87);

    private final List<CheckerRow> rows = new ArrayList<>();
    private final List<SiteResult> completedResults = Collections.synchronizedList(new ArrayList<SiteResult>());
    private final StringBuilder logBuffer = new StringBuilder();

    private ExecutorService executor = Executors.newCachedThreadPool();
    private SharedPreferences prefs;
    private CheckButtonView checkButton;
    private LinearLayout table;
    private TextView verdictView;
    private TextView interfaceView;
    private TextView logButton;
    private volatile boolean checking;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        ensureDefaultCheckers();
        setContentView(createContentView());
        renderRows(loadCheckers());
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private View createContentView() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(COLOR_BG);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(22));
        scrollView.addView(root);

        checkButton = new CheckButtonView(this);
        checkButton.setText("Проверить\nWL");
        checkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!checking) {
                    startCheck();
                }
            }
        });
        root.addView(checkButton, new LinearLayout.LayoutParams(dp(178), dp(178)));

        verdictView = new TextView(this);
        verdictView.setTextColor(COLOR_TEXT);
        verdictView.setTextSize(20);
        verdictView.setGravity(Gravity.CENTER);
        verdictView.setText("Готово к проверке");
        LinearLayout.LayoutParams verdictParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        verdictParams.topMargin = dp(15);
        root.addView(verdictView, verdictParams);

        interfaceView = new TextView(this);
        interfaceView.setTextColor(COLOR_MUTED);
        interfaceView.setTextSize(13);
        interfaceView.setGravity(Gravity.CENTER);
        interfaceView.setText("");
        root.addView(interfaceView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        table = new LinearLayout(this);
        table.setOrientation(LinearLayout.VERTICAL);
        table.setBackgroundColor(COLOR_PANEL);
        LinearLayout.LayoutParams tableParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        tableParams.topMargin = dp(15);
        root.addView(table, tableParams);

        logButton = new TextView(this);
        logButton.setText("Лог");
        logButton.setTextColor(COLOR_TEXT);
        logButton.setTextSize(15);
        logButton.setGravity(Gravity.CENTER);
        logButton.setPadding(dp(14), dp(10), dp(14), dp(10));
        logButton.setBackgroundColor(Color.rgb(32, 42, 58));
        logButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showLogDialog();
            }
        });
        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        logParams.topMargin = dp(12);
        root.addView(logButton, logParams);

        TextView settings = new TextView(this);
        settings.setText("⚙");
        settings.setTextSize(30);
        settings.setGravity(Gravity.CENTER);
        settings.setTextColor(COLOR_TEXT);
        settings.setBackgroundColor(Color.TRANSPARENT);
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showSettingsDialog();
            }
        });
        FrameLayout.LayoutParams settingsParams = new FrameLayout.LayoutParams(dp(56), dp(56));
        settingsParams.gravity = Gravity.TOP | Gravity.RIGHT;
        settingsParams.topMargin = dp(6);
        settingsParams.rightMargin = dp(8);

        frame.addView(scrollView);
        frame.addView(settings, settingsParams);
        return frame;
    }

    private void startCheck() {
        checking = true;
        clearLog();
        completedResults.clear();
        final List<Checker> checkers = loadCheckers();
        appendLog("start checkers=" + checkers.size());
        renderRows(checkers);
        verdictView.setText("Проверка WL...");
        interfaceView.setText("Ищу мобильный интерфейс");
        checkButton.setText("Проверка\nWL...");
        checkButton.setChecking(true);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                performCheck(checkers);
            }
        });
    }

    private void performCheck(final List<Checker> checkers) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            appendLog("ConnectivityManager is null");
            finishWithError("ConnectivityManager недоступен");
            return;
        }

        final CellularLease lease;
        try {
            lease = awaitCellularNetwork(connectivityManager);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            appendLog("await cellular interrupted");
            finishWithError("Проверка прервана");
            return;
        }

        if (lease == null) {
            appendLog("cellular network not found");
            finishWithError("Мобильный интерфейс не найден");
            return;
        }

        Network previousNetwork = connectivityManager.getBoundNetworkForProcess();
        boolean boundToCellular = connectivityManager.bindProcessToNetwork(lease.network);
        appendLog("cellular network=" + lease.network + " interface=" + lease.interfaceName
                + " bindProcessToNetwork=" + boundToCellular);

        try {
            if (!NativeCurlBridge.isLoaded()) {
                appendLog("native curl load error=" + NativeCurlBridge.loadErrorMessage());
                finishWithError("native curl не загружен: " + NativeCurlBridge.loadErrorMessage());
                return;
            }

            final String caPath = CaBundleInstaller.ensureInstalled(this);
            appendLog("caBundle=" + caPath);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    interfaceView.setText("Интерфейс: " + lease.interfaceName);
                }
            });

            final CountDownLatch done = new CountDownLatch(checkers.size());
            final long startedAt = System.currentTimeMillis();
            for (int i = 0; i < checkers.size(); i++) {
                final int index = i;
                final Checker checker = checkers.get(i);
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            SiteResult result = checkSite(lease, checker, caPath);
                            completedResults.add(result);
                            updateRow(index, result);
                        } finally {
                            done.countDown();
                        }
                    }
                });
            }

            boolean allDone = done.await(REQUEST_TIMEOUT_MS + 5000L, TimeUnit.MILLISECONDS);
            appendLog("finished allDone=" + allDone + " elapsedMs=" + (System.currentTimeMillis() - startedAt)
                    + " completed=" + completedResults.size() + "/" + checkers.size());
            final String verdict = calculateVerdict(checkers, new ArrayList<>(completedResults));
            appendLog("verdict=" + verdict);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    finishUi(verdict);
                }
            });
        } catch (Exception e) {
            appendLog("check failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            finishWithError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            connectivityManager.bindProcessToNetwork(previousNetwork);
            appendLog("process network restored=" + previousNetwork);
            lease.close();
        }
    }

    private SiteResult checkSite(CellularLease lease, Checker checker, String caPath) {
        String normalizedUrl = normalizeUrl(checker.value);
        String host = hostFromUrl(normalizedUrl);
        String[] resolveRules = buildResolveRules(lease.network, normalizedUrl, host);
        appendLog("request " + checker.category.title() + " " + normalizedUrl
                + " host=" + host + " iface=" + lease.interfaceName
                + " resolve=" + join(resolveRules));
        NativeCurlBridge.ProbeResponse response = NativeCurlBridge.execute(
                normalizedUrl,
                lease.interfaceName,
                REQUEST_TIMEOUT_MS,
                caPath,
                resolveRules
        );

        if (response.isSuccess()) {
            String detail = "HTTP " + response.httpCode;
            if (!response.primaryIp.isEmpty()) {
                detail += " / " + response.primaryIp;
            }
            appendLog("result OK " + checker.displayName() + " " + detail);
            return new SiteResult(checker, true, detail);
        }
        String detail = response.httpCode > 0
                ? "HTTP " + response.httpCode
                : "curl " + response.curlCode + (response.error.isEmpty() ? "" : ": " + response.error);
        appendLog("result FAIL " + checker.displayName() + " " + detail
                + " primaryIp=" + response.primaryIp);
        return new SiteResult(checker, false, detail);
    }

    private String[] buildResolveRules(Network network, String urlText, String host) {
        if (host.isEmpty()) {
            return new String[0];
        }
        int port = portFromUrl(urlText);
        try {
            InetAddress[] addresses = network.getAllByName(host);
            if (addresses == null || addresses.length == 0) {
                appendLog("dns empty host=" + host);
                return new String[0];
            }
            StringBuilder builder = new StringBuilder();
            builder.append(host).append(":").append(port).append(":");
            for (int i = 0; i < addresses.length; i++) {
                if (i > 0) {
                    builder.append(",");
                }
                builder.append(addresses[i].getHostAddress());
            }
            return new String[]{builder.toString()};
        } catch (Exception error) {
            appendLog("dns error host=" + host + " " + error.getClass().getSimpleName()
                    + ": " + (error.getMessage() == null ? "" : error.getMessage()));
            return new String[0];
        }
    }

    private void updateRow(final int index, final SiteResult result) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                CheckerRow row = rows.get(index);
                row.result.setText(result.success ? "OK" : result.detail);
                row.result.setTextColor(result.success ? COLOR_OK : COLOR_ERR);
            }
        });
    }

    private void finishWithError(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                interfaceView.setText(message == null ? "" : message);
                finishUi("Ошибка проверки");
            }
        });
    }

    private void finishUi(String verdict) {
        checking = false;
        checkButton.setChecking(false);
        checkButton.setText("Проверить\nWL");
        verdictView.setText(verdict);
    }

    private void clearLog() {
        synchronized (logBuffer) {
            logBuffer.setLength(0);
        }
    }

    private void appendLog(String message) {
        synchronized (logBuffer) {
            logBuffer.append(System.currentTimeMillis())
                    .append(" | ")
                    .append(message == null ? "" : message)
                    .append('\n');
        }
    }

    private String currentLog() {
        synchronized (logBuffer) {
            return logBuffer.toString();
        }
    }

    private String join(String[] values) {
        if (values == null || values.length == 0) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append("; ");
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private void showLogDialog() {
        final String logText = currentLog().isEmpty() ? "Лог пока пуст" : currentLog();
        TextView logView = new TextView(this);
        logView.setText(logText);
        logView.setTextColor(COLOR_TEXT);
        logView.setTextSize(12);
        logView.setPadding(dp(14), dp(12), dp(14), dp(12));
        logView.setTextIsSelectable(true);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(logView);

        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Лог проверки")
                .setView(scrollView)
                .setPositiveButton("Копировать", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ClipboardManager clipboard =
                                (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("WLTest log", logText));
                            Toast.makeText(MainActivity.this, "Лог скопирован", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private String calculateVerdict(List<Checker> checkers, List<SiteResult> results) {
        if (results.size() < checkers.size()) {
            return "Частичный результат";
        }

        boolean anyRuOk = false;
        boolean anyNonRuOk = false;
        boolean allRuOk = true;
        boolean allNonRuOk = true;
        boolean allNonRuFailed = true;

        for (SiteResult result : results) {
            if (result.checker.category == Category.RU) {
                anyRuOk = anyRuOk || result.success;
                allRuOk = allRuOk && result.success;
            } else {
                anyNonRuOk = anyNonRuOk || result.success;
                allNonRuOk = allNonRuOk && result.success;
                allNonRuFailed = allNonRuFailed && !result.success;
            }
        }

        if (!anyRuOk && !anyNonRuOk) {
            return "Нет интернета";
        }
        if (allRuOk && allNonRuFailed) {
            return "Белые списки";
        }
        if (allRuOk && allNonRuOk) {
            return "Нет белых списков";
        }
        return "Частичный результат";
    }

    private void renderRows(List<Checker> checkers) {
        rows.clear();
        table.removeAllViews();
        Category previousCategory = null;
        for (Checker checker : checkers) {
            if (checker.category != previousCategory) {
                addCategoryHeader(checker.category);
                previousCategory = checker.category;
            }

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(11), dp(14), dp(11));

            TextView domain = new TextView(this);
            domain.setText(checker.displayName());
            domain.setTextColor(COLOR_TEXT);
            domain.setTextSize(15);

            TextView result = new TextView(this);
            result.setText("Ожидание");
            result.setTextColor(COLOR_MUTED);
            result.setTextSize(14);
            result.setGravity(Gravity.RIGHT);

            row.addView(domain, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(result, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            table.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            rows.add(new CheckerRow(result));
        }
    }

    private void addCategoryHeader(Category category) {
        TextView header = new TextView(this);
        header.setText(category.title());
        header.setTextColor(COLOR_ACCENT);
        header.setTextSize(13);
        header.setGravity(Gravity.LEFT);
        header.setPadding(dp(14), dp(12), dp(14), dp(7));
        header.setBackgroundColor(Color.rgb(18, 24, 34));
        table.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private CellularLease awaitCellularNetwork(ConnectivityManager connectivityManager)
            throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final Network[] selectedNetwork = new Network[1];
        final String[] selectedInterface = new String[1];

        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                captureNetwork(connectivityManager, network, selectedNetwork, selectedInterface, latch);
            }

            @Override
            public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
                selectedNetwork[0] = network;
                String interfaceName = linkProperties.getInterfaceName();
                if (interfaceName != null && !interfaceName.isEmpty()) {
                    selectedInterface[0] = interfaceName;
                    latch.countDown();
                }
            }
        };

        connectivityManager.requestNetwork(request, callback);
        boolean available = latch.await(CELLULAR_WAIT_SECONDS, TimeUnit.SECONDS);
        if (available && selectedNetwork[0] != null && selectedInterface[0] != null) {
            return new CellularLease(connectivityManager, callback, selectedNetwork[0], selectedInterface[0]);
        }

        try {
            connectivityManager.unregisterNetworkCallback(callback);
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private void captureNetwork(
            ConnectivityManager connectivityManager,
            Network network,
            Network[] selectedNetwork,
            String[] selectedInterface,
            CountDownLatch latch
    ) {
        selectedNetwork[0] = network;
        LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
        if (linkProperties == null) {
            return;
        }
        String interfaceName = linkProperties.getInterfaceName();
        if (interfaceName != null && !interfaceName.isEmpty()) {
            selectedInterface[0] = interfaceName;
            latch.countDown();
        }
    }

    private void showSettingsDialog() {
        if (checking) {
            return;
        }
        final String[] categories = {"RU", "не RU"};
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Чекеры")
                .setItems(categories, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        showCategoryEditor(which == 0 ? Category.RU : Category.NON_RU);
                    }
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private void showCategoryEditor(final Category category) {
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                valuesForCategory(category)
        );
        ListView listView = new ListView(this);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                showCheckerInput(category, adapter, position);
            }
        });

        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(category == Category.RU ? "RU чекеры" : "не RU чекеры")
                .setView(listView)
                .setPositiveButton("Добавить", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        showCheckerInput(category, adapter, -1);
                    }
                })
                .setNegativeButton("Назад", null)
                .show();
    }

    private void showCheckerInput(final Category category, final ArrayAdapter<String> adapter, final int position) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(COLOR_TEXT);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        if (position >= 0) {
            input.setText(adapter.getItem(position));
            input.setSelection(input.length());
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(position >= 0 ? "Изменить чекер" : "Добавить чекер")
                .setView(input)
                .setPositiveButton("Сохранить", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String value = input.getText().toString().trim();
                        if (value.isEmpty()) {
                            return;
                        }
                        List<String> values = valuesForCategory(category);
                        if (position >= 0) {
                            values.set(position, value);
                        } else {
                            values.add(value);
                        }
                        saveCategory(category, values);
                        refreshAdapter(adapter, values);
                        renderRows(loadCheckers());
                    }
                })
                .setNegativeButton("Отмена", null);
        if (position >= 0) {
            builder.setNeutralButton("Удалить", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    List<String> values = valuesForCategory(category);
                    values.remove(position);
                    saveCategory(category, values);
                    refreshAdapter(adapter, values);
                    renderRows(loadCheckers());
                }
            });
        }
        builder.show();
    }

    private void refreshAdapter(ArrayAdapter<String> adapter, List<String> values) {
        adapter.clear();
        adapter.addAll(values);
        adapter.notifyDataSetChanged();
    }

    private void ensureDefaultCheckers() {
        if (!prefs.contains(PREF_RU)) {
            prefs.edit()
                    .putString(PREF_RU, DEFAULT_RU)
                    .putString(PREF_NON_RU, DEFAULT_NON_RU)
                    .apply();
        }
    }

    private List<Checker> loadCheckers() {
        List<Checker> output = new ArrayList<>();
        for (String value : valuesForCategory(Category.RU)) {
            output.add(new Checker(Category.RU, value));
        }
        for (String value : valuesForCategory(Category.NON_RU)) {
            output.add(new Checker(Category.NON_RU, value));
        }
        return output;
    }

    private List<String> valuesForCategory(Category category) {
        String raw = prefs.getString(category == Category.RU ? PREF_RU : PREF_NON_RU, "");
        List<String> values = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return values;
        }
        String[] lines = raw.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private void saveCategory(Category category, List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(value);
        }
        prefs.edit()
                .putString(category == Category.RU ? PREF_RU : PREF_NON_RU, builder.toString())
                .apply();
    }

    private String normalizeUrl(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return "https://" + trimmed + "/";
    }

    private String hostFromUrl(String value) {
        try {
            String host = URI.create(value).getHost();
            return host == null ? "" : host;
        } catch (Exception ignored) {
            return "";
        }
    }

    private int portFromUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getPort() > 0) {
                return uri.getPort();
            }
            return "http".equalsIgnoreCase(uri.getScheme()) ? 80 : 443;
        } catch (Exception ignored) {
            return 443;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private enum Category {
        RU,
        NON_RU;

        String title() {
            return this == RU ? "RU чекеры" : "не RU чекеры";
        }
    }

    private static final class Checker {
        final Category category;
        final String value;

        Checker(Category category, String value) {
            this.category = category;
            this.value = value;
        }

        String displayName() {
            String normalized = value;
            if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
                try {
                    String host = URI.create(normalized).getHost();
                    return host == null ? value : host;
                } catch (Exception ignored) {
                    return value;
                }
            }
            return normalized;
        }
    }

    private static final class SiteResult {
        final Checker checker;
        final boolean success;
        final String detail;

        SiteResult(Checker checker, boolean success, String detail) {
            this.checker = checker;
            this.success = success;
            this.detail = detail;
        }
    }

    private static final class CheckerRow {
        final TextView result;

        CheckerRow(TextView result) {
            this.result = result;
        }
    }

    private static final class CellularLease implements AutoCloseable {
        final ConnectivityManager connectivityManager;
        final ConnectivityManager.NetworkCallback callback;
        final Network network;
        final String interfaceName;

        CellularLease(
                ConnectivityManager connectivityManager,
                ConnectivityManager.NetworkCallback callback,
                Network network,
                String interfaceName
        ) {
            this.connectivityManager = connectivityManager;
            this.callback = callback;
            this.network = network;
            this.interfaceName = interfaceName;
        }

        @Override
        public void close() {
            try {
                connectivityManager.unregisterNetworkCallback(callback);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static final class CheckButtonView extends View {
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arcRect = new RectF();
        private String text = "";
        private float angle;
        private ValueAnimator animator;

        CheckButtonView(Context context) {
            super(context);
            fillPaint.setColor(Color.rgb(24, 34, 50));
            textPaint.setColor(COLOR_TEXT);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setFakeBoldText(true);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(5f);
            ringPaint.setColor(Color.rgb(48, 62, 84));
            activePaint.setStyle(Paint.Style.STROKE);
            activePaint.setStrokeWidth(7f);
            activePaint.setStrokeCap(Paint.Cap.ROUND);
            activePaint.setColor(COLOR_ACCENT);
            setClickable(true);
        }

        void setText(String text) {
            this.text = text;
            invalidate();
        }

        void setChecking(boolean checking) {
            if (checking) {
                startAnimator();
            } else {
                stopAnimator();
            }
        }

        private void startAnimator() {
            if (animator != null && animator.isRunning()) {
                return;
            }
            animator = ValueAnimator.ofFloat(0f, 360f);
            animator.setDuration(950L);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    angle = (Float) valueAnimator.getAnimatedValue();
                    invalidate();
                }
            });
            animator.start();
        }

        private void stopAnimator() {
            if (animator != null) {
                animator.cancel();
            }
            angle = 0f;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float size = Math.min(getWidth(), getHeight());
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = size / 2f - 8f;
            canvas.drawCircle(cx, cy, radius, fillPaint);

            arcRect.set(cx - radius + 5f, cy - radius + 5f, cx + radius - 5f, cy + radius - 5f);
            canvas.drawArc(arcRect, 0, 360, false, ringPaint);
            if (animator != null && animator.isRunning()) {
                canvas.drawArc(arcRect, angle, 82f, false, activePaint);
            }

            textPaint.setTextSize(size * 0.14f);
            String[] lines = text.split("\\n");
            float lineHeight = textPaint.getTextSize() * 1.25f;
            float startY = cy - ((lines.length - 1) * lineHeight / 2f) + textPaint.getTextSize() / 3f;
            for (int i = 0; i < lines.length; i++) {
                canvas.drawText(lines[i], cx, startY + i * lineHeight, textPaint);
            }
        }
    }
}
