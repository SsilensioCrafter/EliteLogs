package com.elitelogs.logging;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/**
 * Immutable snapshot of the database section from {@code config.yml}.
 * Centralising the parsing logic keeps {@link LogRouter} tidy and makes it
 * easier to compare settings when /elogs reload is executed.
 */
public final class DatabaseSettings {

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

        String jdbcUrl = trimToNull(section.getString("jdbc-url"));
        if (jdbcUrl == null) {
            String host = trimToNull(section.getString("host", "127.0.0.1"));
            if (host == null) {
                host = "127.0.0.1";
            }
            int port = section.getInt("port", 3306);
            String database = trimToNull(section.getString("database", "elitelogs"));
            if (database == null) {
                database = "elitelogs";
            }
            String params = trimToNull(section.getString("params", "useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true&serverTimezone=UTC&characterEncoding=UTF-8"));
            StringBuilder builder = new StringBuilder("jdbc:mysql://");
            builder.append(host);
            builder.append(':').append(port);
            builder.append('/').append(database);
            if (params != null && !params.isEmpty()) {
                if (!params.startsWith("?")) {
                    builder.append('?');
                }
                builder.append(params.startsWith("?") ? params.substring(1) : params);
            }
            jdbcUrl = builder.toString();
        }

        String username = trimToNull(section.getString("username", "root"));
        if (username == null) {
            username = "root";
        }
        String password = section.getString("password", "");
        int maximumPoolSize = Math.max(1, section.getInt("pool.maximum-pool-size", 8));
        int minimumIdle = Math.max(0, section.getInt("pool.minimum-idle", Math.min(4, maximumPoolSize)));
        long connectionTimeoutMillis = Math.max(1_000L, section.getLong("pool.connection-timeout-millis", 8_000L));
        long maxLifetimeMillis = Math.max(60_000L, section.getLong("pool.max-lifetime-millis", 1_800_000L));
        int batchSize = Math.max(1, section.getInt("batch-size", 100));
        int flushIntervalTicks = Math.max(1, section.getInt("flush-interval-ticks", 2));
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

