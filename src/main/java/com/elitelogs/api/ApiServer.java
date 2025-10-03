package com.elitelogs.api;

import com.elitelogs.EliteLogsPlugin;
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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private volatile ApiConfig activeConfig;

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
        int history = plugin.getConfig().getInt("api.log-history", 250);
        logBuffer.setCapacity(history);

        boolean enabled = plugin.getConfig().getBoolean("api.enabled", false);
        String bind = plugin.getConfig().getString("api.bind", "127.0.0.1");
        int port = plugin.getConfig().getInt("api.port", 9173);
        String tokenRaw = plugin.getConfig().getString("api.auth-token", "");
        String token = tokenRaw != null && !tokenRaw.trim().isEmpty() ? tokenRaw.trim() : null;

        if (!enabled) {
            if (server != null) {
                plugin.getLogger().info("[EliteLogs] API server disabled via config");
            }
            stopServer();
            activeConfig = new ApiConfig(bind, port, token, false);
            return;
        }

        ApiConfig desired = new ApiConfig(bind, port, token, true);
        if (desired.equals(activeConfig) && server != null) {
            return;
        }

        stopServer();
        startServer(desired);
    }

    private void startServer(ApiConfig config) {
        try {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress(config.bind, config.port), 0);
            httpServer.createContext("/api/v1/status", exchange -> handleSafely(exchange, this::handleStatus));
            httpServer.createContext("/api/v1/metrics", exchange -> handleSafely(exchange, this::handleMetrics));
            httpServer.createContext("/api/v1/watchdog", exchange -> handleSafely(exchange, this::handleWatchdog));
            httpServer.createContext("/api/v1/logs", exchange -> handleSafely(exchange, this::handleLogs));
            ExecutorService exec = Executors.newCachedThreadPool(new ApiThreadFactory());
            httpServer.setExecutor(exec);
            httpServer.start();
            this.server = httpServer;
            this.executor = exec;
            this.activeConfig = config;
            plugin.getLogger().info("[EliteLogs] API listening on " + config.bind + ":" + config.port);
        } catch (IOException | IllegalArgumentException ex) {
            plugin.getLogger().severe("[EliteLogs] Failed to start API server: " + ex.getMessage());
            this.activeConfig = config;
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

    private void handleLogs(HttpExchange exchange) throws IOException {
        if (!requireGet(exchange) || !authenticate(exchange)) {
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path == null) {
            sendError(exchange, 404, "Not found");
            return;
        }
        if (path.equals("/api/v1/logs") || path.equals("/api/v1/logs/")) {
            List<String> categories = new ArrayList<>(logBuffer.getCategories());
            categories.sort(Comparator.naturalOrder());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("categories", categories);
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
        int limit = parseLimit(exchange.getRequestURI().getRawQuery(), logBuffer.getCapacity());
        limit = Math.max(1, Math.min(limit, logBuffer.getCapacity()));
        List<String> lines = logBuffer.getRecent(category, limit);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("category", category);
        payload.put("limit", limit);
        payload.put("size", lines.size());
        payload.put("lines", lines);
        sendJson(exchange, 200, payload);
    }

    private int parseLimit(String rawQuery, int fallback) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return fallback;
        }
        StringTokenizer tokenizer = new StringTokenizer(rawQuery, "&");
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            int eq = token.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = token.substring(0, eq);
            if (!"limit".equals(key)) {
                continue;
            }
            String value = token.substring(eq + 1);
            try {
                return Integer.parseInt(urlDecode(value));
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException("UTF-8 not supported", ex);
        }
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
        ApiConfig config = this.activeConfig;
        if (config == null || config.token == null) {
            return true;
        }
        Headers headers = exchange.getRequestHeaders();
        String provided = headers.getFirst("X-API-Key");
        if (provided == null || provided.isEmpty()) {
            provided = getQueryParam(exchange.getRequestURI().getRawQuery(), "token");
        }
        if (provided != null && provided.equals(config.token)) {
            return true;
        }
        sendError(exchange, 401, "Unauthorized");
        return false;
    }

    private String getQueryParam(String rawQuery, String name) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return null;
        }
        StringTokenizer tokenizer = new StringTokenizer(rawQuery, "&");
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            int eq = token.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = token.substring(0, eq);
            if (!name.equals(key)) {
                continue;
            }
            String value = token.substring(eq + 1);
            try {
                return urlDecode(value);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
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
        Map<String, Object> data = new LinkedHashMap<>();
        ApiConfig config = this.activeConfig;
        boolean running = server != null;
        data.put("enabled", running);
        String fallbackBind = plugin.getConfig().getString("api.bind");
        if (fallbackBind == null || fallbackBind.trim().isEmpty()) {
            fallbackBind = "127.0.0.1";
        }
        data.put("bind", config != null ? config.bind : fallbackBind);
        data.put("port", config != null ? config.port : plugin.getConfig().getInt("api.port", 9173));
        data.put("requiresAuth", config != null && config.token != null);
        data.put("logHistory", logBuffer.getCapacity());
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

    private Map<String, Object> buildLastCheck(WatchdogSnapshot snapshot) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", snapshot.getLastCheckMillis());
        data.put("tps", round(snapshot.getLastCheckTps(), 2));
        data.put("errors", snapshot.getLastCheckErrors());
        return data;
    }

    private Map<String, Object> buildLastTrigger(WatchdogSnapshot snapshot) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", snapshot.getLastTriggerMillis());
        data.put("tps", round(snapshot.getLastTriggerTps(), 2));
        data.put("errors", snapshot.getLastTriggerErrors());
        data.put("reason", snapshot.getLastTriggerReason());
        return data;
    }

    private double round(double value, int decimals) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        double scale = Math.pow(10, decimals);
        return Math.round(value * scale) / scale;
    }

    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private static final class ApiConfig {
        final String bind;
        final int port;
        final String token;
        final boolean enabled;

        ApiConfig(String bind, int port, String token, boolean enabled) {
            this.bind = bind;
            this.port = port;
            this.token = token;
            this.enabled = enabled;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ApiConfig)) {
                return false;
            }
            ApiConfig that = (ApiConfig) o;
            return port == that.port && enabled == that.enabled && Objects.equals(bind, that.bind) && Objects.equals(token, that.token);
        }

        @Override
        public int hashCode() {
            return Objects.hash(bind, port, token, enabled);
        }
    }

    private static final class ApiThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "EliteLogs-Api-" + THREAD_COUNTER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
