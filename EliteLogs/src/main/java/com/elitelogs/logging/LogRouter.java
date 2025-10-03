package com.elitelogs.logging;

import com.elitelogs.integration.DiscordAlerter;
import com.elitelogs.players.PlayerTracker;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class LogRouter {
    @Deprecated public static final String GLOBAL_PREFIX = "global-";
    @Deprecated public static final String PLAYER_PREFIX = "players/";

    public interface SinkListener { void onLogged(String category, String line); }

    private final Plugin plugin;
    private final Suppressor suppressor;
    private final Map<String, FileLogger> loggers = new ConcurrentHashMap<>();
    private final List<SinkListener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService writeExecutor;
    private final ZoneId zoneId = ZoneId.systemDefault();
    private final DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
    private final Object databaseLock = new Object();
    private volatile ConfigSnapshot configSnapshot;
    private volatile PlayerTracker playerTracker;
    private volatile DatabaseLogWriter databaseWriter;

    public LogRouter(Plugin plugin) {
        this.plugin = plugin;
        this.suppressor = new Suppressor(plugin);
        this.writeExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "EliteLogs-Writer");
                thread.setDaemon(true);
                return thread;
            }
        });
        reloadConfig();
    }

    public void reloadConfig() {
        this.suppressor.reload();
        ConfigSnapshot snapshot = ConfigSnapshot.from(plugin);
        configureDatabase(snapshot);
        this.configSnapshot = snapshot;
    }

    private void configureDatabase(ConfigSnapshot snapshot) {
        DatabaseSettings newSettings = snapshot != null ? snapshot.databaseSettings : null;
        Collection<String> categoriesForSchema = snapshot != null
                ? snapshot.getEnabledCategories()
                : Collections.emptySet();
        synchronized (databaseLock) {
            DatabaseLogWriter current = this.databaseWriter;
            if (newSettings == null || !newSettings.isEnabled()) {
                if (current != null) {
                    current.close();
                    this.databaseWriter = null;
                    plugin.getLogger().info("[EliteLogs] MySQL storage disabled.");
                }
                return;
            }
            if (current != null && newSettings.equals(current.getSettings())) {
                current.ensureCategories(categoriesForSchema);
                return;
            }
            if (current != null) {
                current.close();
            }
            try {
                DatabaseLogWriter writer = new DatabaseLogWriter(plugin, newSettings, categoriesForSchema);
                this.databaseWriter = writer;
                plugin.getLogger().info("[EliteLogs] MySQL storage ready (" + newSettings.getTablePrefix() + "*").");
            } catch (SQLException ex) {
                this.databaseWriter = null;
                plugin.getLogger().log(Level.SEVERE, "[EliteLogs] Failed to initialise MySQL storage: " + ex.getMessage(), ex);
            }
        }
    }

    public void setPlayerTracker(PlayerTracker tracker) {
        this.playerTracker = tracker;
    }

    public void shutdown() {
        DatabaseLogWriter writer = this.databaseWriter;
        if (writer != null) {
            writer.close();
        }
        writeExecutor.shutdown();
        try {
            if (!writeExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                writeExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            writeExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void addListener(SinkListener listener) {
        if (listener != null) listeners.add(listener);
    }

    public void removeListener(SinkListener listener) {
        if (listener != null) listeners.remove(listener);
    }

    public void setListener(SinkListener listener) {
        listeners.clear();
        addListener(listener);
    }

    public void warn(String message) {
        write("warns", message);
    }

    public void error(String message) {
        write("errors", message);
    }

    public void chat(UUID uuid, String playerName, String message) {
        writeWithPlayer("chat", uuid, playerName, "[chat] " + message);
    }

    public void command(UUID uuid, String playerName, String commandLine) {
        writeWithPlayer("commands", uuid, playerName, "[cmd] " + commandLine);
    }

    public void economy(UUID uuid, String playerName, String message) {
        writeWithPlayer("economy", uuid, playerName, message);
    }

    public void combat(UUID uuid, String playerName, String message) {
        writeWithPlayer("combat", uuid, playerName, message);
    }

    public void inventory(UUID uuid, String playerName, String message) {
        writeWithPlayer("inventory", uuid, playerName, message);
    }

    public void rcon(String message) {
        write("rcon", message);
    }

    public void console(String message) {
        write("console", message);
    }

    public void disconnect(UUID uuid, String playerName, String message) {
        disconnect(DisconnectLogEntry.phase("legacy")
                .player(uuid, playerName)
                .rawMessage(message)
                .build());
    }

    public void disconnect(String message) {
        disconnect(DisconnectLogEntry.phase("legacy")
                .rawMessage(message)
                .build());
    }

    public void disconnect(DisconnectLogEntry entry) {
        if (entry == null) {
            return;
        }
        String message = entry.formatMessage();
        if (message == null || message.isEmpty()) {
            return;
        }
        UUID uuid = entry.getUuid();
        if (uuid != null) {
            writeWithPlayer("disconnects", uuid, entry.resolvePlayerName(), message);
        } else {
            write("disconnects", message);
        }
    }

    public void player(UUID uuid, String playerName, String message) {
        writeWithPlayer("players", uuid, playerName, message);
    }

    public ArchiveManager.Result rotateAll() {
        return rotateAll(false);
    }

    public ArchiveManager.Result rotateAll(boolean includeRecent) {
        if (!includeRecent && !plugin.getConfig().getBoolean("logs.archive", true)) {
            return ArchiveManager.Result.skipped();
        }
        int keep = plugin.getConfig().getInt("logs.keep-days", 30);
        return ArchiveManager.archiveOldLogs(plugin.getDataFolder(), keep, includeRecent);
    }

    public StampedLine write(String category, String message) {
        return write(category, message, DatabaseContext.simple(category));
    }

    private StampedLine write(String category, String message, DatabaseContext context) {
        ConfigSnapshot snapshot = this.configSnapshot;
        if (snapshot == null || !snapshot.isCategoryEnabled(category)) {
            return null;
        }
        Suppressor.Result result = suppressor.filter(category, message);
        if (result.drop) {
            return null;
        }
        Instant timestamp = Instant.now();
        logToDatabase(category, timestamp, result.line, context);
        String stampedLine = stamp(timestamp, result.line);
        append(category, stampedLine, timestamp);
        if ("errors".equals(category)) {
            DiscordAlerter.maybeSend("errors", result.line);
        } else if ("warns".equals(category)) {
            DiscordAlerter.maybeSend("warns", result.line);
        }
        if (result.summary != null && snapshot != null && snapshot.isCategoryEnabled("suppressed")) {
            logToDatabase("suppressed", timestamp, result.summary, DatabaseContext.simple("suppressed"));
            append("suppressed", stamp(timestamp, result.summary), timestamp);
        }
        notifyListeners(category, result.line);
        return new StampedLine(timestamp, stampedLine);
    }

    private void writeWithPlayer(String category, UUID uuid, String playerName, String message) {
        String resolved = resolvePlayerName(uuid, playerName);
        String decorated = decorateLineWithPlayer(uuid, resolved, message);
        StampedLine stamped = write(category, decorated, DatabaseContext.player(category, uuid, resolved));
        if (stamped != null) {
            appendPlayer(category, uuid, resolved, stamped.line, stamped.timestamp);
        }
    }

    private void logToDatabase(String category, Instant timestamp, String message, DatabaseContext context) {
        DatabaseLogWriter writer = this.databaseWriter;
        if (writer != null && message != null && context != null) {
            writer.enqueue(category, timestamp, message, context.playerUuid, context.playerName, context.tags);
        }
    }

    private void append(String category, String stampedLine, Instant timestamp) {
        String file = "global-" + day(timestamp) + ".log";
        FileLogger fileLogger = getLogger(category);
        writeExecutor.execute(() -> fileLogger.append(file, stampedLine));
    }

    private void appendPlayer(String category, UUID uuid, String playerName, String stampedLine, Instant timestamp) {
        ConfigSnapshot snapshot = this.configSnapshot;
        if (snapshot == null || !snapshot.splitByPlayer) {
            return;
        }
        String folder = playerFolder(uuid, playerName);
        String loggerKey = "players".equals(category) ? category + "/" + folder : category + "/players/" + folder;
        FileLogger playerLogger = getLogger(loggerKey);
        String file = day(timestamp) + ".log";
        writeExecutor.execute(() -> playerLogger.append(file, stampedLine));
    }

    private FileLogger getLogger(String category) {
        return loggers.computeIfAbsent(category, key -> new FileLogger(new File(plugin.getDataFolder(), "logs/" + key)));
    }

    private String day(Instant instant) {
        return instant.atZone(zoneId).toLocalDate().format(dayFormatter);
    }

    private String stamp(Instant instant, String message) {
        return "[" + instant.atZone(zoneId).toLocalTime().format(timeFormatter) + "] " + message;
    }

    private void notifyListeners(String category, String line) {
        for (SinkListener listener : listeners) {
            try {
                listener.onLogged(category, line);
            } catch (Throwable ignored) {
            }
        }
    }

    private String playerFolder(UUID uuid, String playerName) {
        PlayerTracker tracker = this.playerTracker;
        if (tracker != null) {
            return tracker.resolveFolder(uuid, playerName);
        }
        if (uuid == null) {
            return "unknown";
        }
        String resolved = resolvePlayerName(uuid, playerName);
        String safeName = sanitizePlayerName(resolved);
        return safeName != null ? safeName + "-" + uuid : uuid.toString();
    }

    private String decorateLineWithPlayer(UUID uuid, String playerName, String message) {
        String resolved = resolvePlayerName(uuid, playerName);
        if (resolved == null || resolved.isEmpty()) {
            resolved = uuid != null ? uuid.toString() : "unknown";
        } else {
            PlayerTracker tracker = this.playerTracker;
            if (tracker != null && uuid != null) {
                tracker.rememberName(uuid, resolved);
            }
        }
        String id = uuid != null ? uuid.toString() : "unknown";
        return "[" + resolved + "|" + id + "] " + message;
    }

    @Deprecated
    public static String decoratePlayerLine(UUID uuid, String playerName, String message) {
        String resolved = (playerName != null && !playerName.isEmpty()) ? playerName : (uuid != null ? uuid.toString() : "unknown");
        String id = uuid != null ? uuid.toString() : "unknown";
        return "[" + resolved + "|" + id + "] " + message;
    }

    @Deprecated
    public static String splitPlayerPath(UUID uuid, String playerName) {
        if (uuid == null) {
            String safe = sanitizePlayerName(playerName);
            return safe != null ? safe : "unknown";
        }
        String safe = sanitizePlayerName(playerName);
        return safe != null ? safe + "-" + uuid : uuid.toString();
    }

    public static String sanitizePlayerName(String playerName) {
        if (playerName == null) {
            return null;
        }
        String normalized = Normalizer.normalize(playerName, Normalizer.Form.NFKC);
        String sanitized = normalized.replaceAll("[^\\p{L}\\p{N}_.-]+", "_");
        sanitized = sanitized.replaceAll("_+", "_");
        sanitized = sanitized.replaceAll("^_+", "");
        sanitized = sanitized.replaceAll("_+$", "");
        if (sanitized.isEmpty()) {
            return null;
        }
        return sanitized;
    }

    private String resolvePlayerName(UUID uuid, String playerName) {
        if (playerName != null && !playerName.isEmpty()) {
            return playerName;
        }
        PlayerTracker tracker = this.playerTracker;
        if (tracker != null && uuid != null) {
            String known = tracker.getLastKnownName(uuid);
            if (known != null && !known.isEmpty()) {
                return known;
            }
        }
        return null;
    }

    public static final class StampedLine {
        public final Instant timestamp;
        public final String line;

        private StampedLine(Instant timestamp, String line) {
            this.timestamp = timestamp;
            this.line = line;
        }
    }

    private static final class DatabaseContext {
        final UUID playerUuid;
        final String playerName;
        final String[] tags;

        private DatabaseContext(UUID playerUuid, String playerName, String[] tags) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.tags = tags;
        }

        static DatabaseContext simple(String category) {
            return new DatabaseContext(null, null, new String[]{"category:" + category});
        }

        static DatabaseContext player(String category, UUID uuid, String resolvedName) {
            String safeName = (resolvedName != null && !resolvedName.isEmpty())
                    ? resolvedName
                    : (uuid != null ? uuid.toString() : "unknown");
            String uuidString = uuid != null ? uuid.toString() : "unknown";
            return new DatabaseContext(uuid, safeName, new String[]{
                    "category:" + category,
                    "player:" + safeName,
                    "uuid:" + uuidString
            });
        }
    }

    private static final class ConfigSnapshot {
        final boolean splitByPlayer;
        final Map<String, Boolean> categories;
        final DatabaseSettings databaseSettings;

        private ConfigSnapshot(boolean splitByPlayer, Map<String, Boolean> categories, DatabaseSettings databaseSettings) {
            this.splitByPlayer = splitByPlayer;
            this.categories = categories;
            this.databaseSettings = databaseSettings;
        }

        static ConfigSnapshot from(Plugin plugin) {
            Map<String, Boolean> categories = new HashMap<>();
            ConfigurationSection section = plugin.getConfig().getConfigurationSection("logs.types");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    categories.put(key, section.getBoolean(key, true));
                }
            }
            boolean split = plugin.getConfig().getBoolean("logs.split-by-player", true);
            DatabaseSettings databaseSettings = DatabaseSettings.from(plugin);
            return new ConfigSnapshot(split, Collections.unmodifiableMap(categories), databaseSettings);
        }

        boolean isCategoryEnabled(String category) {
            Boolean value = categories.get(category);
            if (value == null) {
                return true;
            }
            return value;
        }

        Collection<String> getEnabledCategories() {
            LinkedHashSet<String> enabled = new LinkedHashSet<>(BUILTIN_CATEGORIES.length + categories.size());
            for (String builtin : BUILTIN_CATEGORIES) {
                if (isCategoryEnabled(builtin)) {
                    enabled.add(builtin);
                }
            }
            for (Map.Entry<String, Boolean> entry : categories.entrySet()) {
                if (Boolean.TRUE.equals(entry.getValue())) {
                    enabled.add(entry.getKey());
                }
            }
            return Collections.unmodifiableSet(enabled);
        }

        private static final String[] BUILTIN_CATEGORIES = {
                "warns",
                "errors",
                "chat",
                "commands",
                "players",
                "disconnects",
                "combat",
                "inventory",
                "economy",
                "stats",
                "console",
                "rcon",
                "suppressed"
        };
    }
}
