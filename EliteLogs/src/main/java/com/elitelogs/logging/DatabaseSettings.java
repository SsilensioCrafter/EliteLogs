package com.elitelogs.logging;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Immutable snapshot of the database section from {@code config.yml}.
 * Centralising the parsing logic keeps {@link LogRouter} tidy and makes it
 * easier to compare settings when /elogs reload is executed.
 */
public final class DatabaseSettings {

    private static final String DEFAULT_PARAMS = "useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true&serverTimezone=UTC&characterEncoding=UTF-8";

    private final boolean enabled;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final int maximumPoolSize;
    private final int minimumIdle;
    private final long connectionTimeoutMillis;
    private final long maxLifetimeMillis;
    private final int batchSize;
    private final int flushIntervalTicks;
    private final boolean autoUpgrade;
    private final String tablePrefix;

    private DatabaseSettings(
            boolean enabled,
            String jdbcUrl,
            String username,
            String password,
            int maximumPoolSize,
            int minimumIdle,
            long connectionTimeoutMillis,
            long maxLifetimeMillis,
            int batchSize,
            int flushIntervalTicks,
            boolean autoUpgrade,
            String tablePrefix
    ) {
        this.enabled = enabled;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.maximumPoolSize = maximumPoolSize;
        this.minimumIdle = minimumIdle;
        this.connectionTimeoutMillis = connectionTimeoutMillis;
        this.maxLifetimeMillis = maxLifetimeMillis;
        this.batchSize = batchSize;
        this.flushIntervalTicks = flushIntervalTicks;
        this.autoUpgrade = autoUpgrade;
        this.tablePrefix = tablePrefix;
    }

    public static DatabaseSettings disabled() {
        return new DatabaseSettings(false, null, null, null, 0, 0, 0L, 0L, 0, 1, true, "");
    }

    public static DatabaseSettings from(Plugin plugin) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("storage.database");
        if (section == null) {
            return disabled();
        }

        boolean enabled = section.getBoolean("enabled", false);

        ConfigurationSection connectionSection = section.getConfigurationSection("connection");
        String jdbcUrl = connectionSection != null
                ? trimToNull(connectionSection.getString("jdbc-url"))
                : null;
        if (jdbcUrl == null) {
            jdbcUrl = trimToNull(section.getString("jdbc-url"));
        }
        if (jdbcUrl == null) {
            String host = resolveString(connectionSection, section, "host", "127.0.0.1");
            int port = resolveInt(connectionSection, section, "port", 3306);
            String database = resolveString(connectionSection, section, "database", "elitelogs");
            String params = resolveParams(connectionSection, section);
            StringBuilder builder = new StringBuilder("jdbc:mysql://");
            builder.append(host);
            builder.append(':').append(port);
            builder.append('/').append(database);
            if (params != null && !params.isEmpty()) {
                builder.append('?');
                builder.append(params.startsWith("?") ? params.substring(1) : params);
            }
            jdbcUrl = builder.toString();
        }

        String username = resolveString(connectionSection, section, "username", "elitelogs");
        String password = connectionSection != null
                ? connectionSection.getString("password", section.getString("password", ""))
                : section.getString("password", "");

        ConfigurationSection poolSection = section.getConfigurationSection("pool");
        int maximumPoolSize = poolSection != null
                ? Math.max(1, poolSection.getInt("maximum-pool-size", 8))
                : Math.max(1, section.getInt("pool.maximum-pool-size", 8));
        int minimumIdle = poolSection != null
                ? Math.max(0, poolSection.getInt("minimum-idle", Math.min(4, maximumPoolSize)))
                : Math.max(0, section.getInt("pool.minimum-idle", Math.min(4, maximumPoolSize)));
        long connectionTimeoutMillis = poolSection != null
                ? Math.max(1_000L, poolSection.getLong("connection-timeout-millis", 8_000L))
                : Math.max(1_000L, section.getLong("pool.connection-timeout-millis", 8_000L));
        long maxLifetimeMillis = poolSection != null
                ? Math.max(60_000L, poolSection.getLong("max-lifetime-millis", 1_800_000L))
                : Math.max(60_000L, section.getLong("pool.max-lifetime-millis", 1_800_000L));

        ConfigurationSection batchingSection = section.getConfigurationSection("batching");
        int batchSize = batchingSection != null
                ? Math.max(1, batchingSection.getInt("size", 100))
                : Math.max(1, section.getInt("batch-size", 100));
        int flushIntervalTicks = batchingSection != null
                ? Math.max(1, batchingSection.getInt("flush-interval-ticks", 2))
                : Math.max(1, section.getInt("flush-interval-ticks", 2));

        boolean autoUpgrade = section.getBoolean("auto-upgrade", true);
        String tablePrefix = sanitizePrefix(section.getString("table-prefix", "elitelogs_"));

        return new DatabaseSettings(
                enabled,
                jdbcUrl,
                username,
                password != null ? password : "",
                maximumPoolSize,
                minimumIdle,
                connectionTimeoutMillis,
                maxLifetimeMillis,
                batchSize,
                flushIntervalTicks,
                autoUpgrade,
                tablePrefix
        );
    }

    private static String resolveString(ConfigurationSection primary, ConfigurationSection fallback, String key, String defaultValue) {
        String value = primary != null ? trimToNull(primary.getString(key)) : null;
        if (value == null && fallback != null) {
            value = trimToNull(fallback.getString(key));
        }
        if (value == null) {
            value = defaultValue;
        }
        return value;
    }

    private static int resolveInt(ConfigurationSection primary, ConfigurationSection fallback, String key, int defaultValue) {
        if (primary != null && primary.contains(key)) {
            return primary.getInt(key);
        }
        if (fallback != null && fallback.contains(key)) {
            return fallback.getInt(key);
        }
        return defaultValue;
    }

    private static String resolveParams(ConfigurationSection connectionSection, ConfigurationSection rootSection) {
        if (connectionSection != null) {
            ConfigurationSection properties = connectionSection.getConfigurationSection("properties");
            if (properties != null) {
                Map<String, Object> values = new LinkedHashMap<>(properties.getValues(false));
                if (!values.isEmpty()) {
                    StringJoiner joiner = new StringJoiner("&");
                    for (Map.Entry<String, Object> entry : values.entrySet()) {
                        Object raw = entry.getValue();
                        if (raw == null) {
                            continue;
                        }
                        String key = entry.getKey();
                        String value = trimToNull(String.valueOf(raw));
                        if (key == null || key.isEmpty() || value == null) {
                            continue;
                        }
                        joiner.add(key + "=" + value);
                    }
                    String joined = joiner.toString();
                    if (!joined.isEmpty()) {
                        return joined;
                    }
                }
            }
            String params = trimToNull(connectionSection.getString("params"));
            if (params != null) {
                return params;
            }
        }
        if (rootSection != null) {
            String params = trimToNull(rootSection.getString("params"));
            if (params != null) {
                return params;
            }
        }
        return DEFAULT_PARAMS;
    }

    private static String sanitizePrefix(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return "elitelogs_";
        }
        // Only allow alphanumeric and underscore characters to keep identifiers valid.
        String cleaned = value.replaceAll("[^a-zA-Z0-9_]+", "");
        if (cleaned.isEmpty()) {
            return "elitelogs_";
        }
        return cleaned.endsWith("_") ? cleaned : cleaned + "_";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public int getMinimumIdle() {
        return minimumIdle;
    }

    public long getConnectionTimeoutMillis() {
        return connectionTimeoutMillis;
    }

    public long getMaxLifetimeMillis() {
        return maxLifetimeMillis;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public int getFlushIntervalTicks() {
        return flushIntervalTicks;
    }

    public long getFlushIntervalMillis() {
        return Math.max(50L, flushIntervalTicks * 50L);
    }

    public boolean isAutoUpgrade() {
        return autoUpgrade;
    }

    public String getTablePrefix() {
        return tablePrefix;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DatabaseSettings)) {
            return false;
        }
        DatabaseSettings that = (DatabaseSettings) o;
        return enabled == that.enabled
                && maximumPoolSize == that.maximumPoolSize
                && minimumIdle == that.minimumIdle
                && connectionTimeoutMillis == that.connectionTimeoutMillis
                && maxLifetimeMillis == that.maxLifetimeMillis
                && batchSize == that.batchSize
                && flushIntervalTicks == that.flushIntervalTicks
                && autoUpgrade == that.autoUpgrade
                && Objects.equals(jdbcUrl, that.jdbcUrl)
                && Objects.equals(username, that.username)
                && Objects.equals(password, that.password)
                && Objects.equals(tablePrefix, that.tablePrefix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                enabled,
                jdbcUrl,
                username,
                password,
                maximumPoolSize,
                minimumIdle,
                connectionTimeoutMillis,
                maxLifetimeMillis,
                batchSize,
                flushIntervalTicks,
                autoUpgrade,
                tablePrefix
        );
    }
}

