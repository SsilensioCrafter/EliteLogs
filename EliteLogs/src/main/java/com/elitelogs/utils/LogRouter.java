package com.elitelogs.utils;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class LogRouter {
    @Deprecated public static final String GLOBAL_PREFIX = "global-";
    @Deprecated public static final String PLAYER_PREFIX = "players/";

    public interface SinkListener { void onLogged(String category, String line); }

    private final Plugin plugin;
    private final Suppressor suppressor;
    private final Map<String, FileLogger> loggers = new ConcurrentHashMap<>();
    private final List<SinkListener> listeners = new CopyOnWriteArrayList<>();
    private final ZoneId zoneId = ZoneId.systemDefault();
    private final DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
    private volatile ConfigSnapshot configSnapshot;
    private volatile PlayerTracker playerTracker;

    public LogRouter(Plugin plugin) {
        this.plugin = plugin;
        this.suppressor = new Suppressor(plugin);
        reloadConfig();
    }

    public void reloadConfig() {
        this.suppressor.reload();
        this.configSnapshot = ConfigSnapshot.from(plugin);
    }

    public void setPlayerTracker(PlayerTracker tracker) {
        this.playerTracker = tracker;
    }

    public void addListener(SinkListener listener) {
        if (listener != null) listeners.add(listener);
    }

    public void setListener(SinkListener listener) {
        listeners.clear();
        addListener(listener);
    }

    public void info(String message) {
        write("info", message);
    }

    public void info(UUID uuid, String playerName, String message) {
        writeWithPlayer("info", uuid, playerName, message);
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

    public void player(UUID uuid, String playerName, String message) {
        writeWithPlayer("players", uuid, playerName, message);
    }

    public void rotateAll() {
        ArchiveManager.archiveOldLogs(plugin.getDataFolder(), plugin.getConfig().getInt("logs.keep-days", 30));
    }

    public String write(String category, String message) {
        ConfigSnapshot snapshot = this.configSnapshot;
        if (snapshot == null || !snapshot.isCategoryEnabled(category)) {
            return null;
        }
        Suppressor.Result result = suppressor.filter(category, message);
        if (result.drop) {
            return null;
        }
        String stampedLine = stamp(result.line);
        append(category, stampedLine);
        if ("errors".equals(category)) {
            DiscordAlerter.maybeSend("errors", result.line);
        } else if ("warns".equals(category)) {
            DiscordAlerter.maybeSend("warns", result.line);
        }
        if (result.summary != null && snapshot != null && snapshot.isCategoryEnabled("suppressed")) {
            append("suppressed", stamp(result.summary));
        }
        notifyListeners(category, result.line);
        return stampedLine;
    }

    private void writeWithPlayer(String category, UUID uuid, String playerName, String message) {
        String decorated = decorateLineWithPlayer(uuid, playerName, message);
        String stamped = write(category, decorated);
        if (stamped != null) {
            appendPlayer(category, uuid, playerName, stamped);
        }
    }

    private void append(String category, String stampedLine) {
        String file = "global-" + today() + ".log";
        FileLogger fileLogger = getLogger(category);
        fileLogger.append(file, stampedLine);
    }

    private void appendPlayer(String category, UUID uuid, String playerName, String stampedLine) {
        ConfigSnapshot snapshot = this.configSnapshot;
        if (snapshot == null || !snapshot.splitByPlayer) {
            return;
        }
        String folder = playerFolder(uuid, playerName);
        FileLogger playerLogger = getLogger(category + "/players/" + folder);
        playerLogger.append(today() + ".log", stampedLine);
    }

    private FileLogger getLogger(String category) {
        return loggers.computeIfAbsent(category, key -> new FileLogger(new File(plugin.getDataFolder(), "logs/" + key)));
    }

    private String today() {
        return LocalDate.now(zoneId).format(dayFormatter);
    }

    private String stamp(String message) {
        return "[" + LocalTime.now(zoneId).format(timeFormatter) + "] " + message;
    }

    private void notifyListeners(String category, String line) {
        for (SinkListener listener : listeners) {
            try {
                listener.onLogged(category, line);
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean isCategoryEnabled(String category) {
        ConfigSnapshot snapshot = this.configSnapshot;
        return snapshot == null || snapshot.isCategoryEnabled(category);
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
        String sanitized = playerName.replaceAll("[^A-Za-z0-9_.-]+", "_");
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

    private static final class ConfigSnapshot {
        final boolean splitByPlayer;
        final Map<String, Boolean> categories;

        private ConfigSnapshot(boolean splitByPlayer, Map<String, Boolean> categories) {
            this.splitByPlayer = splitByPlayer;
            this.categories = categories;
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
            return new ConfigSnapshot(split, Collections.unmodifiableMap(categories));
        }

        boolean isCategoryEnabled(String category) {
            Boolean value = categories.get(category);
            if (value == null) {
                return true;
            }
            return value;
        }
    }
}
