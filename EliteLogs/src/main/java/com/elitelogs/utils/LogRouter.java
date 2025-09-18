package com.elitelogs.utils;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class LogRouter {
    public interface SinkListener { void onLogged(String category, String line); }

    private final Plugin plugin;
    private final Suppressor suppressor;
    private final Map<String, FileLogger> loggers = new ConcurrentHashMap<>();
    private final List<SinkListener> listeners = new CopyOnWriteArrayList<>();
    private final ZoneId zoneId = ZoneId.systemDefault();
    private final DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

    public LogRouter(Plugin plugin) {
        this.plugin = plugin;
        this.suppressor = new Suppressor(plugin);
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
        if (!isCategoryEnabled(category)) {
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
        if (result.summary != null && isCategoryEnabled("suppressed")) {
            append("suppressed", stamp(result.summary));
        }
        notifyListeners(category, result.line);
        return stampedLine;
    }

    private void writeWithPlayer(String category, UUID uuid, String playerName, String message) {
        String decorated = decoratePlayerLine(uuid, playerName, message);
        String stamped = write(category, decorated);
        if (stamped != null) {
            appendPlayer(category, uuid, playerName, stamped);
        }
    }

    private void append(String category, String stampedLine) {
        String file = "global-" + today() + ".log";
        logger(category).append(file, stampedLine);
    }

    private void appendPlayer(String category, UUID uuid, String playerName, String stampedLine) {
        if (!plugin.getConfig().getBoolean("logs.split-by-player", true)) {
            return;
        }
        String safeName = sanitizePlayerName(playerName);
        String folder = safeName != null ? safeName + "-" + uuid : uuid.toString();
        logger(category + "/players/" + folder).append(today() + ".log", stampedLine);
    }

    private FileLogger logger(String category) {
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
        if ("suppressed".equals(category)) {
            return plugin.getConfig().getBoolean("logs.types.suppressed", true);
        }
        return plugin.getConfig().getBoolean("logs.types." + category, true);
    }

    private String decoratePlayerLine(UUID uuid, String playerName, String message) {
        String namePart = playerName != null && !playerName.isEmpty() ? playerName : "unknown";
        return "[" + namePart + "|" + uuid + "] " + message;
    }

    public static String sanitizePlayerName(String playerName) {
        if (playerName == null) {
            return null;
        }
        String sanitized = playerName.replaceAll("[^A-Za-z0-9_\-\.]+", "_");
        sanitized = sanitized.replaceAll("_+", "_");
        sanitized = sanitized.replaceAll("^_+", "");
        sanitized = sanitized.replaceAll("_+$", "");
        if (sanitized.isEmpty()) {
            return null;
        }
        return sanitized;
    }
}
