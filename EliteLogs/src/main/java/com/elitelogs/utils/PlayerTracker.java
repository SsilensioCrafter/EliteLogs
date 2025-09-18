package com.elitelogs.utils;

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
        sessionStart.put(p.getUniqueId(), System.currentTimeMillis());
        writeBoth(p, String.format("[LOGIN] %s", ipRegion));
        appendModuleForPlayer(p.getUniqueId(), "info", stamp(String.format("[LOGIN] %s", ipRegion)));
    }

    public void onLogout(Player p){
        long start = sessionStart.getOrDefault(p.getUniqueId(), System.currentTimeMillis());
        long dur = System.currentTimeMillis() - start;
        writeBoth(p, String.format("[LOGOUT] session=%d sec", dur/1000));
        appendModuleForPlayer(p.getUniqueId(), "info", stamp(String.format("[LOGOUT] session=%d sec", dur/1000)));
    }

    public void action(Player p, String text){
        writeBoth(p, text);
        // try to mirror to per-module player file if category can be inferred
        String cat = detectCategory(text);
        if (cat != null) {
            appendModuleForPlayer(p.getUniqueId(), cat, stamp(text));
        }
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
        try {
            File logsRoot = playersRoot.getParentFile(); // .../logs
            File dir = new File(new File(new File(logsRoot, category), "players"), folderFor(uuid));
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

    private void writeBoth(Player player, String line){
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String folder = ensureFolders(player);
        File dir = new File(playersRoot, folder);
        File sessions = new File(dir, "sessions");
        String day = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        File session = new File(sessions, day + ".log");
        append(session, "[" + ts + "] [" + player.getName() + "|" + player.getUniqueId() + "] " + line);
        // NOTE: intentionally no cumulative player.log per user's request
    }

    private String ensureFolders(Player player){
        String folder = folderFor(player.getUniqueId(), player.getName());
        playerFolders.put(player.getUniqueId(), folder);
        lastKnownNames.put(player.getUniqueId(), player.getName());
        File dir = new File(playersRoot, folder);
        if (!dir.exists()) dir.mkdirs();
        File sessions = new File(dir, "sessions");
        if (!sessions.exists()) sessions.mkdirs();
        return folder;
    }

    private String folderFor(UUID uuid){
        String cached = playerFolders.get(uuid);
        if (cached != null) return cached;
        String name = lastKnownNames.get(uuid);
        String folder = folderFor(uuid, name);
        playerFolders.put(uuid, folder);
        return folder;
    }

    private String folderFor(UUID uuid, String playerName){
        String sanitized = LogRouter.sanitizePlayerName(playerName);
        return sanitized != null ? sanitized + "-" + uuid : uuid.toString();
    }

    private void append(File f, String line){
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8))){
            pw.println(line);
        } catch(Exception ignored){}
    }
}
