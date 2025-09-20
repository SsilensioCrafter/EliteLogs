package com.elitelogs.players;

import com.elitelogs.logging.LogRouter;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerTracker {
    private final File playersRoot;
    private final Map<UUID, Long> sessionStart = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerFolders = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastKnownNames = new ConcurrentHashMap<>();

    public PlayerTracker(File dataFolder){
        this.playersRoot = new File(dataFolder, "logs/players");
        if (!playersRoot.exists()) playersRoot.mkdirs();
    }

    public void onLogin(Player p, String ipRegion){
        if (p == null) return;
        UUID uuid = p.getUniqueId();
        String name = p.getName();
        sessionStart.put(uuid, System.currentTimeMillis());
        String message = String.format("[LOGIN] %s", ipRegion);
        logSession(uuid, name, message);
        appendModuleForPlayer(uuid, "info", stamp(message));
    }

    public void onLogout(Player p){
        if (p == null) return;
        UUID uuid = p.getUniqueId();
        String name = p.getName();
        long start = sessionStart.getOrDefault(uuid, System.currentTimeMillis());
        long dur = System.currentTimeMillis() - start;
        String message = String.format("[LOGOUT] session=%d sec", dur/1000);
        logSession(uuid, name, message);
        appendModuleForPlayer(uuid, "info", stamp(message));
    }

    public void action(Player p, String text){
        if (p == null) return;
        UUID uuid = p.getUniqueId();
        String name = p.getName();
        recordAction(uuid, name, text);
    }

    public void action(UUID uuid, String playerName, String text) {
        recordAction(uuid, playerName, text);
    }

    public void rememberName(UUID uuid, String playerName) {
        if (uuid == null) return;
        ensureFolders(uuid, playerName);
    }

    public String getLastKnownName(UUID uuid) {
        return uuid != null ? lastKnownNames.get(uuid) : null;
    }

    public String resolveFolder(UUID uuid, String playerName) {
        if (uuid == null) {
            return "unknown";
        }
        return ensureFolders(uuid, playerName);
    }

    private String detectCategory(String text){
        String low = text.toLowerCase();
        if (low.startsWith("[chat]")) return "chat";
        if (low.startsWith("[cmd]") || low.startsWith("[command]")) return "commands";
        if (low.startsWith("[eco]") || low.startsWith("[economy]")) return "economy";
        if (low.startsWith("[inv]") || low.startsWith("[inventory]")) return "inventory";
        if (low.startsWith("[combat]") || low.contains("killed") || low.contains("death")) return "combat";
        if (low.startsWith("[info]")) return "info";
        if (low.startsWith("[stat]") || low.startsWith("[stats]")) return "stats";
        return null;
    }

    private void appendModuleForPlayer(UUID uuid, String category, String line){
        if (uuid == null || line == null) {
            return;
        }
        try {
            String folder = ensureFolders(uuid, null);
            File logsRoot = playersRoot.getParentFile(); // .../logs
            File dir = new File(new File(new File(logsRoot, category), "players"), folder);
            if (!dir.exists()) dir.mkdirs();
            String day = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            File f = new File(dir, day + ".log");
            append(f, line);
        } catch (Exception ignored){}
    }

    private String stamp(String msg){
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        return "[" + ts + "] " + msg;
    }

    private void recordAction(UUID uuid, String playerName, String text) {
        if (uuid == null || text == null) {
            return;
        }
        logSession(uuid, playerName, text);
        String cat = detectCategory(text);
        if (cat != null) {
            appendModuleForPlayer(uuid, cat, stamp(text));
        }
    }

    private void logSession(UUID uuid, String playerName, String text) {
        if (uuid == null || text == null) {
            return;
        }
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String folder = ensureFolders(uuid, playerName);
        String display = displayName(uuid, playerName);
        File dir = new File(playersRoot, folder);
        File sessions = new File(dir, "sessions");
        String day = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        File session = new File(sessions, day + ".log");
        append(session, "[" + ts + "] [" + display + "|" + uuid + "] " + text);
        // NOTE: intentionally no cumulative player.log per user's request
    }

    private String displayName(UUID uuid, String playerName) {
        if (playerName != null && !playerName.isEmpty()) {
            return playerName;
        }
        String known = uuid != null ? lastKnownNames.get(uuid) : null;
        if (known != null && !known.isEmpty()) {
            return known;
        }
        return uuid != null ? uuid.toString() : "unknown";
    }

    private String ensureFolders(UUID uuid, String playerName){
        if (uuid == null) {
            return "unknown";
        }
        if (playerName != null && !playerName.isEmpty()) {
            lastKnownNames.put(uuid, playerName);
        }
        String current = playerFolders.get(uuid);
        String desired = folderFor(uuid, playerName);
        if (current != null && !current.equals(desired)) {
            File oldDir = new File(playersRoot, current);
            File newDir = new File(playersRoot, desired);
            if (!oldDir.equals(newDir) && oldDir.exists()) {
                // best effort rename so old data follows the player rename
                // ignore failures silently to avoid impacting logging
                //noinspection ResultOfMethodCallIgnored
                oldDir.renameTo(newDir);
            }
        }
        String folder = desired;
        playerFolders.put(uuid, folder);
        File dir = new File(playersRoot, folder);
        if (!dir.exists()) dir.mkdirs();
        File sessions = new File(dir, "sessions");
        if (!sessions.exists()) sessions.mkdirs();
        return folder;
    }

    private String folderFor(UUID uuid){
        return ensureFolders(uuid, null);
    }

    private String folderFor(UUID uuid, String playerName){
        String effective = playerName;
        if ((effective == null || effective.isEmpty()) && uuid != null) {
            String known = lastKnownNames.get(uuid);
            if (known != null && !known.isEmpty()) {
                effective = known;
            }
        }
        String sanitized = LogRouter.sanitizePlayerName(effective);
        return sanitized != null ? sanitized + "-" + uuid : uuid != null ? uuid.toString() : "unknown";
    }

    private void append(File f, String line){
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8))){
            pw.println(line);
        } catch(Exception ignored){}
    }
}
