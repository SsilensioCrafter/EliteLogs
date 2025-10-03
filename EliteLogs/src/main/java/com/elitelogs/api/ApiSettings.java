package com.elitelogs.api;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ApiSettings {
    public enum EndpointKey {
        STATUS("status"),
        METRICS("metrics"),
        WATCHDOG("watchdog"),
        SESSIONS("sessions"),
        LOGS("logs"),
        SEARCH("search");

        private final String key;

        EndpointKey(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        public static EndpointKey fromKey(String key) {
            if (key == null) {
                return null;
            }
            String normalized = key.trim().toLowerCase(Locale.ROOT);
            for (EndpointKey value : values()) {
                if (value.key.equals(normalized)) {
                    return value;
                }
            }
            return null;
        }
    }

    private final boolean enabled;
    private final String bind;
    private final int port;
    private final String authToken;
    private final int logHistory;
    private final String defaultSource;
    private final Map<String, SourceSettings> sources;
    private final Map<EndpointKey, EndpointSettings> endpoints;

    private ApiSettings(boolean enabled, String bind, int port, String authToken, int logHistory, String defaultSource,
                        Map<String, SourceSettings> sources, Map<EndpointKey, EndpointSettings> endpoints) {
        this.enabled = enabled;
        this.bind = bind;
        this.port = port;
        this.authToken = authToken;
        this.logHistory = logHistory;
        this.defaultSource = defaultSource;
        this.sources = Collections.unmodifiableMap(new LinkedHashMap<>(sources));
        this.endpoints = Collections.unmodifiableMap(new LinkedHashMap<>(endpoints));
    }

    public static ApiSettings fromConfig(FileConfiguration config) {
        ConfigurationSection root = config.getConfigurationSection("api");
        boolean enabled = root != null && root.getBoolean("enabled", config.getBoolean("api.enabled", false));
        String bind = root != null ? root.getString("bind", "127.0.0.1") : config.getString("api.bind", "127.0.0.1");
        int port = root != null ? root.getInt("port", 9173) : config.getInt("api.port", 9173);
        String tokenRaw = root != null ? root.getString("auth-token", "") : config.getString("api.auth-token", "");
        String authToken = normalizeToken(tokenRaw);
        int logHistory = root != null ? root.getInt("log-history", 250) : config.getInt("api.log-history", 250);
        if (logHistory <= 0) {
            logHistory = 250;
        }
        String defaultSource = root != null ? root.getString("default-source", "buffer") : "buffer";

        Map<String, SourceSettings> sources = parseSources(root, defaultSource);
        defaultSource = resolveDefaultSource(defaultSource, sources);
        Map<EndpointKey, EndpointSettings> endpoints = parseEndpoints(root, sources, logHistory);

        return new ApiSettings(enabled, bind != null ? bind : "127.0.0.1", port, authToken, logHistory, defaultSource, sources, endpoints);
    }

    private static String normalizeToken(String tokenRaw) {
        if (tokenRaw == null) {
            return null;
        }
        String trimmed = tokenRaw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Map<String, SourceSettings> parseSources(ConfigurationSection root, String defaultSource) {
        Map<String, SourceSettings> sources = new LinkedHashMap<>();
        ConfigurationSection section = root != null ? root.getConfigurationSection("sources") : null;
        sources.put("buffer", buildBufferSource(section));
        sources.put("files", buildFileSource(section));
        sources.put("database", buildDatabaseSource(section));
        if (defaultSource != null && !sources.containsKey(defaultSource)) {
            sources.put(defaultSource, new SourceSettings(defaultSource, true, null));
        }
        return sources;
    }

    private static SourceSettings buildBufferSource(ConfigurationSection section) {
        ConfigurationSection buffer = section != null ? section.getConfigurationSection("buffer") : null;
        boolean enabled = buffer != null ? buffer.getBoolean("enabled", true) : (section == null || section.getBoolean("buffer.enabled", true));
        return new SourceSettings("buffer", enabled, null);
    }

    private static SourceSettings buildFileSource(ConfigurationSection section) {
        ConfigurationSection files = section != null ? section.getConfigurationSection("files") : null;
        boolean enabled = files != null ? files.getBoolean("enabled", true) : (section == null || section.getBoolean("files.enabled", true));
        String rootPath;
        if (files != null && files.isString("root")) {
            rootPath = files.getString("root");
        } else if (section != null && section.isString("files.root")) {
            rootPath = section.getString("files.root");
        } else {
            rootPath = "logs";
        }
        return new SourceSettings("files", enabled, rootPath);
    }

    private static SourceSettings buildDatabaseSource(ConfigurationSection section) {
        ConfigurationSection database = section != null ? section.getConfigurationSection("database") : null;
        boolean enabled = database != null ? database.getBoolean("enabled", true) : (section == null || section.getBoolean("database.enabled", true));
        return new SourceSettings("database", enabled, null);
    }

    private static String resolveDefaultSource(String desired, Map<String, SourceSettings> sources) {
        if (desired != null) {
            SourceSettings current = sources.get(desired);
            if (current != null && current.isEnabled()) {
                return current.getName();
            }
        }
        for (SourceSettings candidate : sources.values()) {
            if (candidate.isEnabled()) {
                return candidate.getName();
            }
        }
        return desired != null ? desired : "buffer";
    }

    private static Map<EndpointKey, EndpointSettings> parseEndpoints(ConfigurationSection root, Map<String, SourceSettings> sources, int logHistory) {
        ConfigurationSection section = root != null ? root.getConfigurationSection("endpoints") : null;
        Map<EndpointKey, EndpointSettings> endpoints = new LinkedHashMap<>();
        for (EndpointKey key : EndpointKey.values()) {
            ConfigurationSection endpointSection = section != null ? section.getConfigurationSection(key.getKey()) : null;
            boolean enabled = endpointSection != null ? endpointSection.getBoolean("enabled", true) : true;
            List<String> allowed = readAllowedSources(endpointSection, sources);
            if (allowed.isEmpty()) {
                allowed = defaultAllowedSources(sources);
            }
            int defaultLimit = endpointSection != null ? endpointSection.getInt("default-limit", defaultLimitFor(key, logHistory)) : defaultLimitFor(key, logHistory);
            endpoints.put(key, new EndpointSettings(key, enabled, allowed, defaultLimit));
        }
        return endpoints;
    }

    private static int defaultLimitFor(EndpointKey key, int logHistory) {
        switch (key) {
            case LOGS:
                return logHistory;
            case SEARCH:
                return Math.min(Math.max(50, logHistory), 1000);
            default:
                return 0;
        }
    }

    private static List<String> readAllowedSources(ConfigurationSection endpointSection, Map<String, SourceSettings> sources) {
        if (endpointSection == null) {
            return Collections.emptyList();
        }
        List<String> raw = endpointSection.getStringList("allow-sources");
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> filtered = new ArrayList<>(raw.size());
        for (String name : raw) {
            if (name == null) {
                continue;
            }
            String normalized = name.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            SourceSettings sourceSettings = sources.get(normalized);
            if (sourceSettings != null && sourceSettings.isEnabled()) {
                filtered.add(sourceSettings.getName());
            }
        }
        return filtered;
    }

    private static List<String> defaultAllowedSources(Map<String, SourceSettings> sources) {
        List<String> allowed = new ArrayList<>();
        for (SourceSettings source : sources.values()) {
            if (source.isEnabled()) {
                allowed.add(source.getName());
            }
        }
        return allowed;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getBind() {
        return bind;
    }

    public int getPort() {
        return port;
    }

    public String getAuthToken() {
        return authToken;
    }

    public int getLogHistory() {
        return logHistory;
    }

    public String getDefaultSource() {
        return defaultSource;
    }

    public Map<String, SourceSettings> getSources() {
        return sources;
    }

    public Map<EndpointKey, EndpointSettings> getEndpoints() {
        return endpoints;
    }

    public SourceSettings getSource(String name) {
        if (name == null) {
            return null;
        }
        return sources.get(name);
    }

    public EndpointSettings getEndpoint(EndpointKey key) {
        return endpoints.get(key);
    }

    public SourceSettings firstAvailableSource() {
        for (SourceSettings source : sources.values()) {
            if (source.isEnabled()) {
                return source;
            }
        }
        return null;
    }

    public String resolveDefaultSourceFor(EndpointKey key) {
        EndpointSettings endpoint = endpoints.get(key);
        if (endpoint != null) {
            List<String> allowed = endpoint.getAllowedSources();
            if (!allowed.isEmpty()) {
                if (allowed.contains(defaultSource)) {
                    return defaultSource;
                }
                return allowed.get(0);
            }
        }
        return defaultSource;
    }

    public static final class SourceSettings {
        private final String name;
        private final boolean enabled;
        private final String rootPath;

        private SourceSettings(String name, boolean enabled, String rootPath) {
            this.name = name;
            this.enabled = enabled;
            this.rootPath = rootPath;
        }

        public String getName() {
            return name;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getRootPath() {
            return rootPath;
        }
    }

    public static final class EndpointSettings {
        private final EndpointKey key;
        private final boolean enabled;
        private final List<String> allowedSources;
        private final int defaultLimit;

        private EndpointSettings(EndpointKey key, boolean enabled, List<String> allowedSources, int defaultLimit) {
            this.key = key;
            this.enabled = enabled;
            this.allowedSources = Collections.unmodifiableList(new ArrayList<>(allowedSources));
            this.defaultLimit = Math.max(0, defaultLimit);
        }

        public EndpointKey getKey() {
            return key;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public List<String> getAllowedSources() {
            return allowedSources;
        }

        public int getDefaultLimit() {
            return defaultLimit;
        }

        public boolean allowsSource(String name) {
            if (name == null) {
                return false;
            }
            return allowedSources.isEmpty() || allowedSources.contains(name);
        }
    }
}
