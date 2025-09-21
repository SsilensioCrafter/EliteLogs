package com.elitelogs.api;

import com.elitelogs.EliteLogsPlugin;
import com.elitelogs.compat.ServerCompat;
import com.elitelogs.logging.LogRouter;
import com.elitelogs.metrics.MetricsCollector;
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

    private volatile HttpServer server;
    private volatile ExecutorService executor;
    private volatile ApiConfig activeConfig;

    public ApiServer(EliteLogsPlugin plugin, LogRouter logRouter, MetricsCollector metricsCollector, SessionManager sessionManager) {
        this.plugin = plugin;
        this.metricsCollector = metricsCollector;
        this.sessionManager = sessionManager;
        this.logRouter = logRouter;
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
        ApiConfig config = this.activeConfig;
        StringBuilder json = new StringBuilder();
        json.append('{');
        json.append("\"plugin\":{");
        json.append("\"name\":").append(JsonUtil.quote(plugin.getDescription().getName())).append(',');
        json.append("\"version\":").append(JsonUtil.quote(plugin.getDescription().getVersion())).append('}');
        json.append(',');

        json.append("\"server\":{");
        json.append("\"version\":").append(JsonUtil.quote(ServerCompat.describeServerVersion())).append(',');
        json.append("\"supportedRange\":").append(JsonUtil.quote(ServerCompat.describeSupportedRange())).append(',');
        json.append("\"onlinePlayers\":").append(ServerCompat.getOnlinePlayerCount());
        json.append('}');
        json.append(',');

        json.append("\"config\":{");
        json.append("\"language\":").append(JsonUtil.quote(plugin.getConfig().getString("language", "en"))).append(',');
        json.append("\"debug\":").append(plugin.getConfig().getBoolean("debug", false)).append(',');
        json.append("\"logs\":{");
        json.append("\"splitByPlayer\":").append(plugin.getConfig().getBoolean("logs.split-by-player", true));
        ConfigurationSection logsSection = plugin.getConfig().getConfigurationSection("logs.types");
        if (logsSection != null) {
            List<String> keys = new ArrayList<>(logsSection.getKeys(false));
            Collections.sort(keys);
            if (!keys.isEmpty()) {
                json.append(',');
                json.append("\"categories\":{");
                for (int i = 0; i < keys.size(); i++) {
                    String key = keys.get(i);
                    json.append(JsonUtil.quote(key)).append(':').append(logsSection.getBoolean(key, true));
                    if (i + 1 < keys.size()) {
                        json.append(',');
                    }
                }
                json.append('}');
            }
        }
        json.append('}');
        json.append(',');

        json.append("\"sessions\":{");
        json.append("\"enabled\":").append(plugin.getConfig().getBoolean("sessions.enabled", true)).append(',');
        json.append("\"autosaveMinutes\":").append(plugin.getConfig().getInt("sessions.autosave-minutes", 10)).append(',');
        json.append("\"saveGlobal\":").append(plugin.getConfig().getBoolean("sessions.save-global", true)).append(',');
        json.append("\"savePlayers\":").append(plugin.getConfig().getBoolean("sessions.save-players", true));
        json.append('}');
        json.append(',');

        json.append("\"metrics\":{");
        json.append("\"enabled\":").append(plugin.getConfig().getBoolean("metrics.enabled", true)).append(',');
        json.append("\"intervalSeconds\":").append(plugin.getConfig().getInt("metrics.interval-seconds", 60));
        json.append('}');
        json.append(',');

        json.append("\"watchdog\":{");
        json.append("\"enabled\":").append(plugin.getConfig().getBoolean("watchdog.enabled", true)).append(',');
        json.append("\"tpsThreshold\":").append(plugin.getConfig().getDouble("watchdog.tps-threshold", 5.0)).append(',');
        json.append("\"errorThreshold\":").append(plugin.getConfig().getInt("watchdog.error-threshold", 50));
        json.append('}');
        json.append(',');

        json.append("\"api\":{");
        boolean running = server != null;
        json.append("\"enabled\":").append(running).append(',');
        json.append("\"bind\":").append(JsonUtil.quote(config != null ? config.bind : plugin.getConfig().getString("api.bind", "127.0.0.1"))).append(',');
        json.append("\"port\":").append(config != null ? config.port : plugin.getConfig().getInt("api.port", 9173)).append(',');
        json.append("\"requiresAuth\":").append(config != null && config.token != null).append(',');
        json.append("\"logHistory\":").append(logBuffer.getCapacity());
        json.append('}');
        json.append('}');

        markResponded(exchange);
        JsonResponse.send(exchange, 200, json.toString());
    }

    private void handleMetrics(HttpExchange exchange) throws IOException {
        if (!requireGet(exchange) || !authenticate(exchange)) {
            return;
        }
        StringBuilder json = new StringBuilder();
        json.append('{');
        double tps = metricsCollector != null ? metricsCollector.getCurrentTPS() : 0.0;
        double cpu = metricsCollector != null ? metricsCollector.getCpuLoadPercent() : 0.0;
        long heapUsed = metricsCollector != null ? metricsCollector.getHeapUsedMB() : 0L;
        long heapMax = metricsCollector != null ? metricsCollector.getHeapMaxMB() : 0L;
        json.append("\"tps\":").append(String.format(java.util.Locale.ROOT, "%.2f", tps)).append(',');
        json.append("\"cpuLoad\":").append(String.format(java.util.Locale.ROOT, "%.1f", cpu)).append(',');
        json.append("\"heapUsedMB\":").append(heapUsed).append(',');
        json.append("\"heapMaxMB\":").append(heapMax).append(',');
        json.append("\"onlinePlayers\":").append(ServerCompat.getOnlinePlayerCount()).append(',');

        json.append("\"session\":{");
        boolean running = sessionManager != null && sessionManager.isRunning();
        json.append("\"running\":").append(running).append(',');
        long startedAt = sessionManager != null ? sessionManager.getSessionStartMillis() : 0L;
        json.append("\"startedAt\":").append(startedAt).append(',');
        long uptime = sessionManager != null ? sessionManager.getUptimeSeconds() : 0L;
        json.append("\"uptimeSeconds\":").append(uptime).append(',');
        int joins = sessionManager != null ? sessionManager.getJoinCount() : 0;
        int warns = sessionManager != null ? sessionManager.getWarnCount() : 0;
        int errors = sessionManager != null ? sessionManager.getErrorCount() : 0;
        json.append("\"joins\":").append(joins).append(',');
        json.append("\"warns\":").append(warns).append(',');
        json.append("\"errors\":").append(errors);
        json.append('}');
        json.append(',');

        json.append("\"watchdog\":{");
        json.append("\"enabled\":").append(plugin.getConfig().getBoolean("watchdog.enabled", true)).append(',');
        json.append("\"tpsThreshold\":").append(plugin.getConfig().getDouble("watchdog.tps-threshold", 5.0)).append(',');
        json.append("\"errorThreshold\":").append(plugin.getConfig().getInt("watchdog.error-threshold", 50));
        json.append('}');
        json.append('}');

        markResponded(exchange);
        JsonResponse.send(exchange, 200, json.toString());
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
            StringBuilder json = new StringBuilder();
            json.append('{');
            json.append("\"categories\":[");
            for (int i = 0; i < categories.size(); i++) {
                json.append(JsonUtil.quote(categories.get(i)));
                if (i + 1 < categories.size()) {
                    json.append(',');
                }
            }
            json.append(']');
            json.append('}');
            markResponded(exchange);
            JsonResponse.send(exchange, 200, json.toString());
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
        StringBuilder json = new StringBuilder();
        json.append('{');
        json.append("\"category\":").append(JsonUtil.quote(category)).append(',');
        json.append("\"limit\":").append(limit).append(',');
        json.append("\"size\":").append(lines.size()).append(',');
        json.append("\"lines\":[");
        for (int i = 0; i < lines.size(); i++) {
            json.append(JsonUtil.quote(lines.get(i)));
            if (i + 1 < lines.size()) {
                json.append(',');
            }
        }
        json.append(']');
        json.append('}');
        markResponded(exchange);
        JsonResponse.send(exchange, 200, json.toString());
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
        markResponded(exchange);
        JsonResponse.send(exchange, status, "{\"error\":" + JsonUtil.quote(message) + "}");
    }

    private void markResponded(HttpExchange exchange) {
        exchange.setAttribute(ATTR_RESPONSE_SENT, Boolean.TRUE);
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
