package com.elitelogs.logging;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles mirroring log entries into MySQL without blocking the classic file
 * writers. Entries are queued and flushed by a dedicated worker thread that
 * batches inserts for better throughput. The schema is created automatically
 * on first connection and upgraded whenever new metadata is required.
 */
public final class DatabaseLogWriter implements AutoCloseable {

    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_EVENT_TYPE_LENGTH = 64;

    private final Plugin plugin;
    private final DatabaseSettings settings;
    private final HikariDataSource dataSource;
    private final LinkedBlockingQueue<DbEntry> queue;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread workerThread;
    private final Logger logger;
    private final Map<String, String> tableNames = new ConcurrentHashMap<>();
    private final Set<String> ensuredTables = ConcurrentHashMap.newKeySet();
    private final Map<String, String> insertStatements = new ConcurrentHashMap<>();
    private final String tablePrefix;
    private final long flushIntervalMillis;
    private final boolean autoUpgrade;
    private final String schemaInfoTable;
    private final String registryTable;

    public DatabaseLogWriter(Plugin plugin, DatabaseSettings settings, Collection<String> initialCategories) throws SQLException {
        this.plugin = plugin;
        this.settings = settings;
        this.logger = plugin.getLogger();
        this.tablePrefix = settings.getTablePrefix();
        this.flushIntervalMillis = settings.getFlushIntervalMillis();
        this.autoUpgrade = settings.isAutoUpgrade();
        this.schemaInfoTable = tablePrefix + "schema_info";
        this.registryTable = tablePrefix + "registry";
        this.queue = new LinkedBlockingQueue<>();

        List<String> categories = initialCategories != null
                ? new ArrayList<>(new LinkedHashSet<>(initialCategories))
                : Collections.emptyList();

        try {
            this.dataSource = createDataSource(settings);
        } catch (RuntimeException ex) {
            throw new SQLException("Failed to configure connection pool: " + ex.getMessage(), ex);
        }

        try (Connection connection = dataSource.getConnection()) {
            initialiseSchema(connection, categories);
        }

        this.workerThread = new Thread(this::runLoop, "EliteLogs-MySQLWriter");
        this.workerThread.setDaemon(true);
        this.workerThread.start();
    }

    public DatabaseSettings getSettings() {
        return settings;
    }

    public void ensureCategories(Collection<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            for (String category : categories) {
                if (category == null || category.trim().isEmpty()) {
                    continue;
                }
                String table = tableNameFor(category);
                ensureTable(connection, category, table);
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "[EliteLogs] Failed to ensure MySQL tables: " + ex.getMessage(), ex);
        }
    }

    public void enqueue(String category, Instant timestamp, String message, UUID playerUuid, String playerName, String[] tags) {
        if (!running.get()) {
            return;
        }
        if (category == null || category.isEmpty() || message == null) {
            return;
        }
        DbEntry entry = new DbEntry(category, timestamp, message, playerUuid, playerName, tags);
        queue.offer(entry);
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        queue.offer(DbEntry.poison());
        try {
            workerThread.join(5_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            dataSource.close();
        }
    }

    private void runLoop() {
        List<DbEntry> buffer = new ArrayList<>(Math.max(1, settings.getBatchSize()));
        long nextFlushDeadline = System.currentTimeMillis() + flushIntervalMillis;
        try {
            while (true) {
                long wait = Math.max(1L, nextFlushDeadline - System.currentTimeMillis());
                DbEntry entry = queue.poll(wait, TimeUnit.MILLISECONDS);
                if (entry == null) {
                    flushQuietly(buffer);
                    buffer.clear();
                    nextFlushDeadline = System.currentTimeMillis() + flushIntervalMillis;
                    continue;
                }
                if (entry.isPoison()) {
                    flushQuietly(buffer);
                    break;
                }
                buffer.add(entry);
                if (buffer.size() >= settings.getBatchSize()) {
                    flushQuietly(buffer);
                    buffer.clear();
                    nextFlushDeadline = System.currentTimeMillis() + flushIntervalMillis;
                } else {
                    nextFlushDeadline = System.currentTimeMillis() + flushIntervalMillis;
                }
            }
        } catch (InterruptedException ignored) {
        } finally {
            if (!buffer.isEmpty()) {
                flushQuietly(buffer);
                buffer.clear();
            }
        }
    }

    private void flushQuietly(List<DbEntry> buffer) {
        if (buffer.isEmpty()) {
            return;
        }
        try {
            flush(buffer);
        } catch (Exception ex) {
            logger.log(Level.WARNING, "[EliteLogs] Database flush failed: " + ex.getMessage(), ex);
        }
    }

    private void flush(List<DbEntry> buffer) throws SQLException {
        if (buffer.isEmpty()) {
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Map<String, List<DbEntry>> grouped = groupByTable(connection, buffer);
                for (Map.Entry<String, List<DbEntry>> entry : grouped.entrySet()) {
                    List<DbEntry> entries = entry.getValue();
                    if (entries.isEmpty()) {
                        continue;
                    }
                    String sql = insertStatements.computeIfAbsent(entry.getKey(), this::buildInsertSql);
                    try (PreparedStatement ps = connection.prepareStatement(sql)) {
                        for (DbEntry dbEntry : entries) {
                            bindEntry(ps, dbEntry);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private Map<String, List<DbEntry>> groupByTable(Connection connection, List<DbEntry> buffer) throws SQLException {
        Map<String, List<DbEntry>> grouped = new LinkedHashMap<>();
        for (DbEntry entry : buffer) {
            if (!entry.isValid()) {
                continue;
            }
            String table = tableNameFor(entry.category);
            ensureTable(connection, entry.category, table);
            grouped.computeIfAbsent(table, key -> new ArrayList<>()).add(entry);
        }
        return grouped;
    }

    private void initialiseSchema(Connection connection, Collection<String> categories) throws SQLException {
        if (autoUpgrade) {
            ensureSchemaInfoTable(connection);
        }
        ensureRegistryTable(connection);
        if (categories == null) {
            return;
        }
        for (String category : categories) {
            if (category == null || category.trim().isEmpty()) {
                continue;
            }
            String table = tableNameFor(category);
            ensureTable(connection, category, table);
        }
    }

    private void ensureSchemaInfoTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS `" + schemaInfoTable + "` (" +
                    "id TINYINT NOT NULL PRIMARY KEY," +
                    "schema_version INT NOT NULL," +
                    "applied_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO `" + schemaInfoTable + "` (id, schema_version) VALUES (1, ?) " +
                        "ON DUPLICATE KEY UPDATE schema_version = VALUES(schema_version), applied_at = CURRENT_TIMESTAMP(6)")) {
            ps.setInt(1, SCHEMA_VERSION);
            ps.executeUpdate();
        }
    }

    private void ensureRegistryTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS `" + registryTable + "` (" +
                    "category VARCHAR(64) NOT NULL PRIMARY KEY," +
                    "table_name VARCHAR(64) NOT NULL," +
                    "created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)," +
                    "updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    private void registerCategory(Connection connection, String category, String table) throws SQLException {
        if (category == null || category.isEmpty()) {
            return;
        }
        ensureRegistryTable(connection);
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO `" + registryTable + "` (category, table_name) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE table_name = VALUES(table_name), updated_at = CURRENT_TIMESTAMP(6)")) {
            ps.setString(1, category);
            ps.setString(2, table);
            ps.executeUpdate();
        }
    }

    private void ensureTable(Connection connection, String category, String table) throws SQLException {
        if (ensuredTables.contains(table)) {
            return;
        }
        synchronized (ensuredTables) {
            if (ensuredTables.contains(table)) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                String timeIndex = indexName(table, "time");
                String playerIndex = indexName(table, "player");
                String eventIndex = indexName(table, "event");
                statement.execute("CREATE TABLE IF NOT EXISTS `" + table + "` (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                        "occurred_at TIMESTAMP(6) NOT NULL," +
                        "event_type VARCHAR(64) NOT NULL," +
                        "message TEXT NOT NULL," +
                        "player_uuid BINARY(16) NULL," +
                        "player_name VARCHAR(64) NULL," +
                        "tags JSON NOT NULL," +
                        "context JSON NOT NULL," +
                        "INDEX `" + timeIndex + "` (occurred_at)," +
                        "INDEX `" + playerIndex + "` (player_uuid, occurred_at)," +
                        "INDEX `" + eventIndex + "` (event_type, occurred_at)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            }
            if (autoUpgrade) {
                upgradeTable(connection, table);
            }
            registerCategory(connection, category, table);
            ensuredTables.add(table);
        }
    }

    private void upgradeTable(Connection connection, String table) throws SQLException {
        Set<String> columns = getExistingColumns(connection, table);
        List<String> ddl = new ArrayList<>();
        if (!columns.contains("event_type")) {
            ddl.add("ALTER TABLE `" + table + "` ADD COLUMN event_type VARCHAR(64) NOT NULL DEFAULT 'unknown' AFTER occurred_at");
        }
        if (!columns.contains("context")) {
            ddl.add("ALTER TABLE `" + table + "` ADD COLUMN context JSON NOT NULL AFTER tags");
        }
        if (!ddl.isEmpty()) {
            try (Statement statement = connection.createStatement()) {
                for (String sql : ddl) {
                    statement.execute(sql);
                }
            }
            if (!columns.contains("event_type")) {
                String fallback = inferCategoryFromTableName(table);
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE `" + table + "` SET event_type = ? WHERE event_type IS NULL OR event_type = '' OR event_type = 'unknown'")) {
                    ps.setString(1, fallback);
                    ps.executeUpdate();
                }
            }
        }
        ensureIndexes(connection, table);
    }

    private void ensureIndexes(Connection connection, String table) throws SQLException {
        Set<String> indexes = getExistingIndexes(connection, table);
        String timeIndex = indexName(table, "time");
        String playerIndex = indexName(table, "player");
        String eventIndex = indexName(table, "event");
        try (Statement statement = connection.createStatement()) {
            if (!indexes.contains(timeIndex)) {
                statement.execute("CREATE INDEX `" + timeIndex + "` ON `" + table + "` (occurred_at)");
            }
            if (!indexes.contains(playerIndex)) {
                statement.execute("CREATE INDEX `" + playerIndex + "` ON `" + table + "` (player_uuid, occurred_at)");
            }
            if (!indexes.contains(eventIndex)) {
                statement.execute("CREATE INDEX `" + eventIndex + "` ON `" + table + "` (event_type, occurred_at)");
            }
        }
    }

    private Set<String> getExistingColumns(Connection connection, String table) throws SQLException {
        Set<String> columns = new LinkedHashSet<>();
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getColumns(connection.getCatalog(), null, table, null)) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                if (name != null) {
                    columns.add(name.toLowerCase(Locale.ROOT));
                }
            }
        }
        return columns;
    }

    private Set<String> getExistingIndexes(Connection connection, String table) throws SQLException {
        Set<String> indexes = new LinkedHashSet<>();
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getIndexInfo(connection.getCatalog(), null, table, false, false)) {
            while (rs.next()) {
                String name = rs.getString("INDEX_NAME");
                if (name != null) {
                    indexes.add(name);
                }
            }
        }
        return indexes;
    }

    private String buildInsertSql(String table) {
        return "INSERT INTO `" + table + "` (occurred_at, event_type, message, player_uuid, player_name, tags, context) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
    }

    private void bindEntry(PreparedStatement ps, DbEntry entry) throws SQLException {
        ps.setTimestamp(1, Timestamp.from(entry.timestamp));
        ps.setString(2, eventTypeFor(entry));
        ps.setString(3, entry.message);
        if (entry.playerUuid != null) {
            ps.setBytes(4, uuidToBytes(entry.playerUuid));
        } else {
            ps.setNull(4, Types.BINARY);
        }
        String playerName = sanitizePlayerName(entry.playerName);
        if (playerName != null) {
            ps.setString(5, playerName);
        } else {
            ps.setNull(5, Types.VARCHAR);
        }
        ps.setString(6, toJsonArray(entry.tags));
        ps.setString(7, toContextJson(entry));
    }

    private String tableNameFor(String category) {
        return tableNames.computeIfAbsent(category, this::buildTableName);
    }

    private String buildTableName(String category) {
        String sanitized = sanitizeCategory(category);
        int maxTableLength = 64;
        int allowedCategoryLength = Math.max(1, maxTableLength - tablePrefix.length());
        if (sanitized.length() > allowedCategoryLength) {
            sanitized = sanitized.substring(0, allowedCategoryLength);
        }
        String candidate = tablePrefix + sanitized;
        if (candidate.length() > maxTableLength) {
            candidate = candidate.substring(0, maxTableLength);
        }
        return candidate;
    }

    private static String sanitizeCategory(String category) {
        if (category == null || category.isEmpty()) {
            return "misc";
        }
        String normalized = category.trim().toLowerCase(Locale.ROOT);
        String cleaned = normalized.replaceAll("[^a-z0-9_]+", "_");
        cleaned = cleaned.replaceAll("_+", "_");
        cleaned = cleaned.replaceAll("^_+", "");
        cleaned = cleaned.replaceAll("_+$", "");
        if (cleaned.isEmpty()) {
            cleaned = "misc";
        }
        return cleaned;
    }

    private String indexName(String table, String suffix) {
        String base = "idx_" + table + '_' + suffix;
        if (base.length() <= 60) {
            return base;
        }
        String hash = Integer.toUnsignedString(table.hashCode(), 36);
        return "idx_" + suffix + '_' + hash;
    }

    private static HikariDataSource createDataSource(DatabaseSettings settings) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(settings.getJdbcUrl());
        config.setUsername(settings.getUsername());
        config.setPassword(settings.getPassword());
        config.setMaximumPoolSize(settings.getMaximumPoolSize());
        config.setMinimumIdle(settings.getMinimumIdle());
        config.setConnectionTimeout(settings.getConnectionTimeoutMillis());
        config.setMaxLifetime(settings.getMaxLifetimeMillis());
        config.setPoolName("EliteLogs-MySQL");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        return new HikariDataSource(config);
    }

    private String inferCategoryFromTableName(String table) {
        String base = table;
        if (base != null && tablePrefix != null && base.startsWith(tablePrefix)) {
            base = base.substring(tablePrefix.length());
        }
        return sanitizeCategory(base);
    }

    private String eventTypeFor(DbEntry entry) {
        String type = extractTagValue(entry.tags, "type");
        if (type == null) {
            type = extractTagValue(entry.tags, "category");
        }
        if (type == null) {
            type = entry.category;
        }
        String sanitized = sanitizeCategory(type);
        if (sanitized.length() > MAX_EVENT_TYPE_LENGTH) {
            sanitized = sanitized.substring(0, MAX_EVENT_TYPE_LENGTH);
        }
        if (sanitized.isEmpty()) {
            return "misc";
        }
        return sanitized;
    }

    private static String extractTagValue(String[] tags, String key) {
        if (tags == null || key == null) {
            return null;
        }
        String needle = key.toLowerCase(Locale.ROOT);
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            int colon = tag.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String candidate = tag.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            if (candidate.equals(needle)) {
                String value = tag.substring(colon + 1).trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    private static byte[] uuidToBytes(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        byte[] bytes = new byte[16];
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) ((msb >>> (8 * (7 - i))) & 0xFF);
        }
        for (int i = 0; i < 8; i++) {
            bytes[8 + i] = (byte) ((lsb >>> (8 * (7 - i))) & 0xFF);
        }
        return bytes;
    }

    private static String toJsonArray(String[] tags) {
        if (tags == null || tags.length == 0) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < tags.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            String value = tags[i] != null ? tags[i] : "";
            builder.append('"').append(escapeJson(value)).append('"');
        }
        builder.append(']');
        return builder.toString();
    }

    private String toContextJson(DbEntry entry) {
        Map<String, String> context = new LinkedHashMap<>();
        if (entry.category != null && !entry.category.isEmpty()) {
            context.put("category", entry.category);
        }
        if (entry.playerName != null && !entry.playerName.isEmpty()) {
            context.put("player", entry.playerName);
        }
        if (entry.playerUuid != null) {
            context.put("uuid", entry.playerUuid.toString());
        }
        if (entry.tags != null) {
            int index = 0;
            for (String tag : entry.tags) {
                if (tag == null || tag.isEmpty()) {
                    index++;
                    continue;
                }
                int colon = tag.indexOf(':');
                String key;
                String value;
                if (colon > 0) {
                    key = sanitizeJsonKey(tag.substring(0, colon));
                    value = tag.substring(colon + 1);
                } else {
                    key = "tag_" + index;
                    value = tag;
                }
                key = ensureUniqueKey(context, key);
                context.put(key, value != null ? value : "");
                index++;
            }
        }
        if (context.isEmpty()) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : context.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            builder.append('"').append(escapeJson(entry.getKey())).append('"').append(':');
            builder.append('"').append(escapeJson(entry.getValue())).append('"');
            first = false;
        }
        builder.append('}');
        return builder.toString();
    }

    private static String ensureUniqueKey(Map<String, String> existing, String baseKey) {
        String key = baseKey;
        int counter = 2;
        while (existing.containsKey(key)) {
            key = baseKey + '_' + counter++;
        }
        return key;
    }

    private static String sanitizeJsonKey(String raw) {
        if (raw == null) {
            return "tag";
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        String cleaned = normalized.replaceAll("[^a-z0-9_]+", "_");
        cleaned = cleaned.replaceAll("_+", "_");
        cleaned = cleaned.replaceAll("^_+", "");
        cleaned = cleaned.replaceAll("_+$", "");
        if (cleaned.isEmpty()) {
            cleaned = "tag";
        }
        return cleaned;
    }

    private static String sanitizePlayerName(String playerName) {
        if (playerName == null) {
            return null;
        }
        String trimmed = playerName.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 64) {
            trimmed = trimmed.substring(0, 64);
        }
        return trimmed;
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                builder.append('\\');
            }
            builder.append(c);
        }
        return builder.toString();
    }

    private static final class DbEntry {
        private final String category;
        private final Instant timestamp;
        private final String message;
        private final UUID playerUuid;
        private final String playerName;
        private final String[] tags;
        private final boolean poison;

        private DbEntry(String category, Instant timestamp, String message, UUID playerUuid, String playerName, String[] tags) {
            this(category, timestamp, message, playerUuid, playerName, tags, false);
        }

        private DbEntry(String category, Instant timestamp, String message, UUID playerUuid, String playerName, String[] tags, boolean poison) {
            this.category = category;
            this.timestamp = timestamp != null ? timestamp : Instant.now();
            this.message = message;
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.tags = tags != null ? tags : new String[0];
            this.poison = poison;
        }

        static DbEntry poison() {
            return new DbEntry(null, Instant.now(), null, null, null, new String[0], true);
        }

        boolean isPoison() {
            return poison;
        }

        boolean isValid() {
            return !poison && category != null && !category.isEmpty() && message != null;
        }
    }
}
