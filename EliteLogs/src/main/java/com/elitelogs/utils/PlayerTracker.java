package com.elitelogs.utils;

import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerTracker {
    private final File playersRoot;
    private final Map<UUID, Long> sessionStart = new HashMap<>();

    public PlayerTracker(File dataFolder){
        this.playersRoot = new File(dataFolder, "logs/players");
        if (!playersRoot.exists()) playersRoot.mkdirs();
    }

    private File getPlayerDir(Player p){
        File d = new File(playersRoot, p.getName());
        if (!d.exists()) d.mkdirs();
        File sessions = new File(d, "sessions");
        if (!sessions.exists()) sessions.mkdirs();
        return d;
    }

    public void onLogin(Player p, String ipRegion){
        sessionStart.put(p.getUniqueId(), System.currentTimeMillis());
        writeBoth(p.getName(), String.format("[LOGIN] %s UUID=%s", ipRegion, p.getUniqueId()));
        // also mirror to module 'info' for this player (optional)
        appendModuleForPlayer(p.getUniqueId(), "info", stamp(String.format("[LOGIN] %s", ipRegion)));
    }

    public void onLogout(Player p){
        long start = sessionStart.getOrDefault(p.getUniqueId(), System.currentTimeMillis());
        long dur = System.currentTimeMillis() - start;
        writeBoth(p.getName(), String.format("[LOGOUT] session=%d sec", dur/1000));
        appendModuleForPlayer(p.getUniqueId(), "info", stamp(String.format("[LOGOUT] session=%d sec", dur/1000)));
    }

    public void action(Player p, String text){
        writeBoth(p.getName(), text);
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
            File dir = new File(new File(new File(logsRoot, category), "players"), uuid.toString());
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

    private void writeBoth(String player, String line){
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        File dir = new File(playersRoot, player);
        if (!dir.exists()) dir.mkdirs();
        File sessions = new File(dir, "sessions");
        if (!sessions.exists()) sessions.mkdirs();
        String day = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        File session = new File(sessions, day + ".log");
        append(session, "[" + ts + "] " + line);
        // NOTE: intentionally no cumulative player.log per user's request
    }

    private void append(File f, String line){
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8))){
            pw.println(line);
        } catch(Exception ignored){}
    }
}
