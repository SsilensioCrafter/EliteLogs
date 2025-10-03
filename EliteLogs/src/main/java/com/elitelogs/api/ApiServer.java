package com.elitelogs.api;

import com.elitelogs.EliteLogsPlugin;
import com.elitelogs.api.ApiSettings.EndpointKey;
import com.elitelogs.api.ApiSettings.EndpointSettings;
import com.elitelogs.api.ApiSettings.SourceSettings;
import com.elitelogs.api.provider.BufferLogProvider;
import com.elitelogs.api.provider.DatabaseLogProvider;
import com.elitelogs.api.provider.FileLogProvider;
import com.elitelogs.api.provider.LogDataProvider;
import com.elitelogs.compat.ServerCompat;
import com.elitelogs.logging.LogRouter;
import com.elitelogs.metrics.MetricsCollector;
import com.elitelogs.metrics.Watchdog;
import com.elitelogs.metrics.Watchdog.WatchdogSnapshot;
import com.elitelogs.reporting.SessionManager;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class ApiServer {
    private static final String ATTR_RESPONSE_SENT = "elitelogs.responseSent";
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

    private final EliteLogsPlugin plugin;
    private final MetricsCollector metricsCollector;
    private final SessionManager sessionManager;
    private final ApiLogBuffer logBuffer;
    private final LogRouter logRouter;
    private final Watchdog watchdog;

    private volatile HttpServer server;
    private volatile ExecutorService executor;
    private volatile RuntimeState runtime;

    public ApiServer(EliteLogsPlugin plugin, LogRouter logRouter, MetricsCollector metricsCollector,
                     SessionManager sessionManager, Watchdog watchdog) {
        this.plugin = plugin;
        this.metricsCollector = metricsCollector;
        this.sessionManager = sessionManager;
        this.logRouter = logRouter;
        this.watchdog = watchdog;
        this.logBuffer = new ApiLogBuffer();
        this.logRouter.addListener(logBuffer);
    }

    public synchronized void start() {
        applyConfig();
    }

    public synchronized void stop() {
        stopServer();
    }

    public synchronized void reload() {
        applyConfig();
    }

    private void applyConfig() {
        ApiSettings settings = ApiSettings.fromConfig(plugin.getConfig());
        logBuffer.setCapacity(settings.getLogHistory());
        RuntimeState desired = buildRuntimeState(settings);
        this.runtime = desired;

        if (!settings.isEnabled()) {
            if (server != null) {
                plugin.getLogger().info("[EliteLogs] API server disabled via config");
            }
            stopServer();
            return;
        }

        stopServer();
        startServer(desired);
    }

    private RuntimeState buildRuntimeState(ApiSettings settings) {
        Map<String, LogDataProvider> providers = new LinkedHashMap<>();
        for (SourceSettings source : settings.getSources().values()) {
            if (!source.isEnabled()) {
                continue;
            }
            String name = source.getName();
            switch (name) {
                case "buffer":
                    providers.put(name, new BufferLogProvider(logBuffer));
                    break;
                case "files":
                    providers.put(name, new FileLogProvider(plugin, logRouter, source.getRootPath()));
                    break;
                case "database":
                    providers.put(name, new DatabaseLogProvider(logRouter));
                    break;
                default:
                    providers.put(name, new BufferLogProvider(logBuffer));
                    break;
            }
        }
        if (providers.isEmpty()) {
            providers.put("buffer", new BufferLogProvider(logBuffer));
        }
        return new RuntimeState(settings, settings.getBind(), settings.getPort(), settings.getAuthToken(), providers);
    }

    private void startServer(RuntimeState state) {
        try {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress(state.bind, state.port), 0);
            if (state.settings.getEndpoint(EndpointKey.STATUS).isEnabled()) {
                httpServer.createContext("/api/v1/status", exchange -> handleSafely(exchange, this::handleStatus));
            }
            if (state.settings.getEndpoint(EndpointKey.METRICS).isEnabled()) {
                httpServer.createContext("/api/v1/metrics", exchange -> handleSafely(exchange, this::handleMetrics));
            }
            if (state.settings.getEndpoint(EndpointKey.WATCHDOG).isEnabled()) {
                httpServer.createContext("/api/v1/watchdog", exchange -> handleSafely(exchange, this::handleWatchdog));
            }
            if (state.settings.getEndpoint(EndpointKey.SESSIONS).isEnabled()) {
                httpServer.createContext("/api/v1/sessions", exchange -> handleSafely(exchange, this::handleSessions));
            }
            if (state.settings.getEndpoint(EndpointKey.LOGS).isEnabled()) {
                httpServer.createContext("/api/v1/logs", exchange -> handleSafely(exchange, this::handleLogs));
            }
            if (state.settings.getEndpoint(EndpointKey.SEARCH).isEnabled()) {
                httpServer.createContext("/api/v1/logs/search", exchange -> handleSafely(exchange, this::handleLogsSearch));
            }
            ExecutorService exec = Executors.newCachedThreadPool(new ApiThreadFactory());
            httpServer.setExecutor(exec);
            httpServer.start();
            this.server = httpServer;
            this.executor = exec;
            this.runtime = state;
            plugin.getLogger().info("[EliteLogs] API listening on " + state.bind + ":" + state.port);
        } catch (IOException | IllegalArgumentException ex) {
            plugin.getLogger().severe("[EliteLogs] Failed to start API server: " + ex.getMessage());
            stopServer();
        }
    }

    private void stopServer() {
        HttpServer httpServer = this.server;
        if (httpServer != null) {
            httpServer.stop(0);
            this.server = null;
        }
        ExecutorService exec = this.executor;
        if (exec != null) {
            exec.shutdownNow();
            this.executor = null;
        }
    }

    private void handleSafely(HttpExchange exchange, ExchangeHandler handler) {
        try {
            handler.handle(exchange);
        } catch (Throwable ex) {
            plugin.getLogger().warning("[EliteLogs] API request failed: " + ex.getMessage());
            if (!Boolean.TRUE.equals(exchange.getAttribute(ATTR_RESPONSE_SENT))) {
                try {
                    sendError(exchange, 500, "Internal server error");
                } catch (IOException ignored) {
                }
            }
        } finally {
            exchange.close();
        }
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!requireGet(exchange) || !authenticate(exchange)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("plugin", buildPluginInfo());
        payload.put("server", buildServerInfo());
        payload.put("config", buildConfigInfo());
        payload.put("api", buildApiInfo());
        sendJson(exchange, 200, payload);
    }

    private void handleMetrics(HttpExchange exchange) throws IOException {
        if (!requireGet(exchange) || !authenticate(exchange)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tps", round(metricsCollector != null ? metricsCollector.getCurrentTPS() : 0.0, 2));
        payload.put("cpuLoad", round(metricsCollector != null ? metricsCollector.getCpuLoadPercent() : 0.0, 1));
        payload.put("heapUsedMB", metricsCollector != null ? metricsCollector.getHeapUsedMB() : 0L);
        payload.put("heapMaxMB", metricsCollector != null ? metricsCollector.getHeapMaxMB() : 0L);
        payload.put("onlinePlayers", ServerCompat.getOnlinePlayerCount());
        payload.put("session", buildSessionSnapshot());
        Map<String, Object> watchdogInfo = new LinkedHashMap<>(buildWatchdogConfig());
        watchdogInfo.putAll(buildWatchdogRuntime());
        payload.put("watchdog", watchdogInfo);
        sendJson(exchange, 200, payload);
    }

    private void handleWatchdog(HttpExchange exchange) throws IOException {
        if (!requireGet(exchange) || !authenticate(exchange)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("config", buildWatchdogConfig());
        payload.put("runtime", buildWatchdogRuntime());
        sendJson(exchange, 200, payload);
    }

    private void handleSessions(HttpExchange exchange) throws IOException {
        if (!requireGet(exchange) || !authenticate(exchange)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("current", buildSessionSnapshot());
        payload.put("history", buildSessionHistory(10));
        sendJson(exchange, 200, payload);
    }

    private void handleLogs(HttpExchange exchange) throws IOException {
        if (!requireGet(exchange) || !authenticate(exchange)) {
            return;
        }
        RuntimeState state = this.runtime;
        if (state == null) {
            sendError(exchange, 503, "API not initialised");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path == null) {
            sendError(exchange, 404, "Not found");
            return;
        }
        if (path.equals("/api/v1/logs") || path.equals("/api/v1/logs/")) {
            Map<String, Object> payload = new LinkedHashMap<>();
            EndpointSettings endpoint = state.settings.getEndpoint(EndpointKey.LOGS);
            payload.put("defaultSource", state.settings.resolveDefaultSourceFor(EndpointKey.LOGS));
            payload.put("allowedSources", endpoint != null ? endpoint.getAllowedSources() : Collections.emptyList());
            payload.put("availableSources", describeAvailableSources(state, EndpointKey.LOGS));
            payload.put("sources", buildSourceCatalog(state));
            payload.put("categories", buildKnownCategories());
            sendJson(exchange, 200, payload);
            return;
        }
        if (!path.startsWith("/api/v1/logs/")) {
            sendError(exchange, 404, "Not found");
            return;
        }
        String encoded = path.substring("/api/v1/logs/".length());
        if (encoded.isEmpty()) {
            sendError(exchange, 404, "Not found");
            return;
        }
        String category = urlDecode(encoded);
        Map<String, List<String>> query = parseQueryParameters(exchange.getRequestURI().getRawQuery());
        String requestedSource = firstParam(query, "source");
        LogDataProvider provider = resolveProvider(state, EndpointKey.LOGS, requestedSource);
        if (provider == null) {
            sendError(exchange, 404, "Source unavailable");
            return;
        }
        if (!provider.isAvailable()) {
            sendError(exchange, 503, "Source temporarily unavailable");
            return;
        }
        EndpointSettings endpoint = state.settings.getEndpoint(EndpointKey.LOGS);
        int limit = resolveLimit(query, endpoint);
        String queryText = firstParam(query, "q");
        List<Map<String, Object>> records = (queryText != null && !queryText.trim().isEmpty())
                ? provider.search(category, queryText, limit)
                : provider.fetch(category, limit);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("category", category);
        payload.put("source", provider.getName());
        payload.put("limit", limit);
        if (queryText != null && !queryText.trim().isEmpty()) {
            payload.put("query", queryText);
        }
        payload.put("availableSources", describeAvailableSources(state, EndpointKey.LOGS));
        payload.put("size", records.size());
        payload.put("records", records);
        sendJson(exchange, 200, payload);
    }

    private void handleLogsSearch(HttpExchange exchange) throws IOException {
        if (!requireGet(exchange) || !authenticate(exchange)) {
            return;
        }
        RuntimeState state = this.runtime;
        if (state == null) {
            sendError(exchange, 503, "API not initialised");
            return;
        }
        Map<String, List<String>> query = parseQueryParameters(exchange.getRequestURI().getRawQuery());
        String category = firstParam(query, "category");
        if (category == null || category.trim().isEmpty()) {
            sendError(exchange, 400, "Missing category parameter");
            return;
        }
        String q = firstParam(query, "q");
        if (q == null || q.trim().isEmpty()) {
            sendError(exchange, 400, "Missing q parameter");
            return;
        }
        String requestedSource = firstParam(query, "source");
        LogDataProvider provider = resolveProvider(state, EndpointKey.SEARCH, requestedSource);
        if (provider == null) {
            sendError(exchange, 404, "Source unavailable");
            return;
        }
        if (!provider.isAvailable()) {
            sendError(exchange, 503, "Source temporarily unavailable");
            return;
        }
        EndpointSettings endpoint = state.settings.getEndpoint(EndpointKey.SEARCH);
        int limit = resolveLimit(query, endpoint);
        List<Map<String, Object>> records = provider.search(category, q, limit);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("category", category);
        payload.put("query", q);
        payload.put("source", provider.getName());
        payload.put("limit", limit);
        payload.put("availableSources", describeAvailableSources(state, EndpointKey.SEARCH));
        payload.put("size", records.size());
        payload.put("records", records);
        sendJson(exchange, 200, payload);
    }

    private Map<String, Object> buildPluginInfo() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", plugin.getDescription().getName());
        data.put("version", plugin.getDescription().getVersion());
        return data;
    }

    private Map<String, Object> buildServerInfo() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", ServerCompat.describeServerVersion());
        data.put("supportedRange", ServerCompat.describeSupportedRange());
        data.put("onlinePlayers", ServerCompat.getOnlinePlayerCount());
        return data;
    }

    private Map<String, Object> buildConfigInfo() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("language", plugin.getConfig().getString("language", "en"));
        data.put("debug", plugin.getConfig().getBoolean("debug", false));
        data.put("logs", buildLogsConfig());
        data.put("sessions", buildSessionsConfig());
        data.put("metrics", buildMetricsConfig());
        data.put("watchdog", buildWatchdogConfig());
        return data;
    }

    private Map<String, Object> buildLogsConfig() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("splitByPlayer", plugin.getConfig().getBoolean("logs.split-by-player", true));
        ConfigurationSection logsSection = plugin.getConfig().getConfigurationSection("logs.types");
        if (logsSection != null) {
            List<String> keys = new ArrayList<>(logsSection.getKeys(false));
            Collections.sort(keys);
            if (!keys.isEmpty()) {
                Map<String, Object> categories = new LinkedHashMap<>();
                for (String key : keys) {
                    categories.put(key, logsSection.getBoolean(key, true));
                }
                data.put("categories", categories);
            }
        }
        return data;
    }

    private Map<String, Object> buildSessionsConfig() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", plugin.getConfig().getBoolean("sessions.enabled", true));
        data.put("autosaveMinutes", plugin.getConfig().getInt("sessions.autosave-minutes", 10));
        data.put("saveGlobal", plugin.getConfig().getBoolean("sessions.save-global", true));
        data.put("savePlayers", plugin.getConfig().getBoolean("sessions.save-players", true));
        return data;
    }

    private Map<String, Object> buildMetricsConfig() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", plugin.getConfig().getBoolean("metrics.enabled", true));
        data.put("intervalSeconds", plugin.getConfig().getInt("metrics.interval-seconds", 60));
        return data;
    }

    private Map<String, Object> buildWatchdogConfig() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", plugin.getConfig().getBoolean("watchdog.enabled", true));
        data.put("tpsThreshold", plugin.getConfig().getDouble("watchdog.tps-threshold", 5.0));
        data.put("errorThreshold", plugin.getConfig().getInt("watchdog.error-threshold", 50));
        return data;
    }

    private Map<String, Object> buildWatchdogRuntime() {
        Map<String, Object> data = new LinkedHashMap<>();
        if (watchdog == null) {
            data.put("running", false);
            data.put("intervalSeconds", Watchdog.SAMPLE_INTERVAL_SECONDS);
            data.put("pendingErrors", 0);
            data.put("triggerCount", 0);
            data.put("lastCheck", null);
            data.put("lastTrigger", null);
            return data;
        }
        WatchdogSnapshot snapshot = watchdog.snapshot();
        data.put("running", snapshot.isRunning());
        data.put("intervalSeconds", snapshot.getIntervalSeconds());
        data.put("pendingErrors", snapshot.getPendingErrors());
        data.put("triggerCount", snapshot.getTriggerCount());
        data.put("lastCheck", snapshot.getLastCheckMillis() > 0L ? buildLastCheck(snapshot) : null);
        data.put("lastTrigger", snapshot.getLastTriggerMillis() > 0L ? buildLastTrigger(snapshot) : null);
        return data;
    }

    private Map<String, Object> buildApiInfo() {
        RuntimeState state = this.runtime;
        Map<String, Object> data = new LinkedHashMap<>();
        boolean running = server != null && state != null && state.settings.isEnabled();
        data.put("running", running);
        String bind = state != null ? state.bind : plugin.getConfig().getString("api.bind", "127.0.0.1");
        data.put("bind", bind);
        data.put("port", state != null ? state.port : plugin.getConfig().getInt("api.port", 9173));
        data.put("requiresAuth", state != null && state.token != null);
        data.put("logHistory", logBuffer.getCapacity());
        data.put("defaultSource", state != null ? state.settings.getDefaultSource() : null);
        data.put("sources", buildSourceCatalog(state));
        data.put("endpoints", buildEndpointCatalog(state));
        return data;
    }

    private Map<String, Object> buildSessionSnapshot() {
        Map<String, Object> data = new LinkedHashMap<>();
        boolean running = sessionManager != null && sessionManager.isRunning();
        data.put("running", running);
        data.put("startedAt", sessionManager != null ? sessionManager.getSessionStartMillis() : 0L);
        data.put("uptimeSeconds", sessionManager != null ? sessionManager.getUptimeSeconds() : 0L);
        data.put("joins", sessionManager != null ? sessionManager.getJoinCount() : 0);
        data.put("warns", sessionManager != null ? sessionManager.getWarnCount() : 0);
        data.put("errors", sessionManager != null ? sessionManager.getErrorCount() : 0);
        return data;
    }

    private List<Map<String, Object>> buildSessionHistory(int limit) {
        File folder = new File(plugin.getDataFolder(), "reports/sessions");
        if (!folder.exists() || !folder.isDirectory()) {
            return Collections.emptyList();
        }
        File[] files = folder.listFiles(file -> file.isFile() && file.getName().endsWith(".yml") && !"last-session.yml".equals(file.getName()));
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }
        List<File> sorted = new ArrayList<>();
        Collections.addAll(sorted, files);
        sorted.sort(Comparator.comparingLong(File::lastModified).reversed());
        List<Map<String, Object>> history = new ArrayList<>();
        int max = Math.min(limit, sorted.size());
        for (int i = 0; i < max; i++) {
            File file = sorted.get(i);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("file", file.getName());
            entry.put("lastModified", Instant.ofEpochMilli(file.lastModified()).toString());
            entry.put("size", file.length());
            history.add(entry);
        }
        return history;
    }

    private List<String> buildKnownCategories() {
        Set<String> categories = new LinkedHashSet<>(logRouter.getActiveCategories());
        categories.addAll(logBuffer.getCategories());
        List<String> list = new ArrayList<>(categories);
        Collections.sort(list);
        return list;
    }

    private Map<String, Object> buildSourceCatalog(RuntimeState state) {
        Map<String, Object> catalog = new LinkedHashMap<>();
        ApiSettings settings = state != null ? state.settings : ApiSettings.fromConfig(plugin.getConfig());
        for (Map.Entry<String, SourceSettings> entry : settings.getSources().entrySet()) {
            String name = entry.getKey();
            SourceSettings cfg = entry.getValue();
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("enabled", cfg.isEnabled());
            if (cfg.getRootPath() != null) {
                info.put("root", cfg.getRootPath());
            }
            LogDataProvider provider = state != null ? state.providers.get(name) : null;
            boolean available = provider != null && provider.isAvailable();
            info.put("available", available);
            if (available) {
                info.put("categories", provider.listCategories());
            }
            catalog.put(name, info);
        }
        return catalog;
    }

    private Map<String, Object> buildEndpointCatalog(RuntimeState state) {
        Map<String, Object> catalog = new LinkedHashMap<>();
        ApiSettings settings = state != null ? state.settings : ApiSettings.fromConfig(plugin.getConfig());
        for (EndpointKey key : EndpointKey.values()) {
            EndpointSettings endpoint = settings.getEndpoint(key);
            if (endpoint == null) {
                continue;
            }
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("enabled", endpoint.isEnabled());
            info.put("allowedSources", endpoint.getAllowedSources());
            if (endpoint.getDefaultLimit() > 0) {
                info.put("defaultLimit", endpoint.getDefaultLimit());
            }
            catalog.put(key.getKey(), info);
        }
        return catalog;
    }

    private boolean requireGet(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            sendError(exchange, 405, "Method not allowed");
            return false;
        }
        return true;
    }

    private boolean authenticate(HttpExchange exchange) throws IOException {
        RuntimeState state = this.runtime;
        String token = state != null ? state.token : null;
        if (token == null) {
            return true;
        }
        Headers headers = exchange.getRequestHeaders();
        String provided = headers.getFirst("X-API-Key");
        if (provided == null || provided.isEmpty()) {
            provided = firstParam(parseQueryParameters(exchange.getRequestURI().getRawQuery()), "token");
        }
        if (provided != null && provided.equals(token)) {
            return true;
        }
        sendError(exchange, 401, "Unauthorized");
        return false;
    }

    private Map<String, List<String>> parseQueryParameters(String rawQuery) {
        Map<String, List<String>> params = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return params;
        }
        StringTokenizer tokenizer = new StringTokenizer(rawQuery, "&");
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            if (token.isEmpty()) {
                continue;
            }
            int eq = token.indexOf('=');
            String key;
            String value;
            if (eq >= 0) {
                key = urlDecode(token.substring(0, eq));
                value = urlDecode(token.substring(eq + 1));
            } else {
                key = urlDecode(token);
                value = "";
            }
            params.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
        }
        return params;
    }

    private String firstParam(Map<String, List<String>> params, String key) {
        List<String> values = params.get(key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    private String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException("UTF-8 not supported", ex);
        }
    }

    private int resolveLimit(Map<String, List<String>> query, EndpointSettings endpoint) {
        int fallback = endpoint != null && endpoint.getDefaultLimit() > 0 ? endpoint.getDefaultLimit() : logBuffer.getCapacity();
        String rawLimit = firstParam(query, "limit");
        if (rawLimit == null || rawLimit.trim().isEmpty()) {
            return Math.max(1, fallback);
        }
        try {
            int parsed = Integer.parseInt(rawLimit.trim());
            return Math.max(1, Math.min(parsed, 5_000));
        } catch (NumberFormatException ignored) {
            return Math.max(1, fallback);
        }
    }

    private LogDataProvider resolveProvider(RuntimeState state, EndpointKey key, String requested) {
        String candidate = requested != null && !requested.trim().isEmpty()
                ? requested.trim()
                : state.settings.resolveDefaultSourceFor(key);
        EndpointSettings endpoint = state.settings.getEndpoint(key);
        if (endpoint != null && !endpoint.getAllowedSources().isEmpty() && !endpoint.getAllowedSources().contains(candidate)) {
            return null;
        }
        LogDataProvider provider = state.providers.get(candidate);
        if (provider != null) {
            return provider;
        }
        if (endpoint != null && !endpoint.getAllowedSources().isEmpty()) {
            for (String allowed : endpoint.getAllowedSources()) {
                LogDataProvider fallback = state.providers.get(allowed);
                if (fallback != null) {
                    return fallback;
                }
            }
        }
        return state.providers.get(state.settings.getDefaultSource());
    }

    private List<String> describeAvailableSources(RuntimeState state, EndpointKey key) {
        List<String> available = new ArrayList<>();
        EndpointSettings endpoint = state.settings.getEndpoint(key);
        for (Map.Entry<String, LogDataProvider> entry : state.providers.entrySet()) {
            String name = entry.getKey();
            LogDataProvider provider = entry.getValue();
            if (endpoint != null && !endpoint.getAllowedSources().isEmpty() && !endpoint.getAllowedSources().contains(name)) {
                continue;
            }
            if (provider.isAvailable()) {
                available.add(name);
            }
        }
        Collections.sort(available);
        return available;
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", message);
        sendJson(exchange, status, payload);
    }

    private void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        markResponded(exchange);
        JsonResponse.send(exchange, status, JsonUtil.stringify(payload));
    }

    private void markResponded(HttpExchange exchange) {
        exchange.setAttribute(ATTR_RESPONSE_SENT, Boolean.TRUE);
    }

    private Map<String, Object> buildLastCheck(WatchdogSnapshot snapshot) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", snapshot.getLastCheckMillis());
        data.put("agoSeconds", (System.currentTimeMillis() - snapshot.getLastCheckMillis()) / 1000.0);
        return data;
    }

    private Map<String, Object> buildLastTrigger(WatchdogSnapshot snapshot) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", snapshot.getLastTriggerMillis());
        data.put("reason", snapshot.getLastTriggerReason());
        data.put("agoSeconds", (System.currentTimeMillis() - snapshot.getLastTriggerMillis()) / 1000.0);
        return data;
    }

    private double round(double value, int places) {
        double factor = Math.pow(10, places);
        return Math.round(value * factor) / factor;
    }

    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private static final class RuntimeState {
        private final ApiSettings settings;
        private final String bind;
        private final int port;
        private final String token;
        private final Map<String, LogDataProvider> providers;

        private RuntimeState(ApiSettings settings, String bind, int port, String token, Map<String, LogDataProvider> providers) {
            this.settings = settings;
            this.bind = bind;
            this.port = port;
            this.token = token;
            this.providers = Collections.unmodifiableMap(new LinkedHashMap<>(providers));
        }

        @Override
        public int hashCode() {
            return Objects.hash(bind, port, token);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof RuntimeState)) {
                return false;
            }
            RuntimeState other = (RuntimeState) obj;
            return port == other.port && Objects.equals(bind, other.bind) && Objects.equals(token, other.token);
        }
    }

    private static final class ApiThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "EliteLogs-API-" + THREAD_COUNTER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
