package com.elitelogs.utils;

import org.bukkit.plugin.Plugin;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class LogRouter {
    private final Plugin plugin;
    private final Suppressor suppressor;
    private final SimpleDateFormat dayFmt = new SimpleDateFormat("yyyy-MM-dd");
    private final Map<String, FileLogger> loggers = new HashMap<>();

    public interface SinkListener { void onLogged(String category, String line); }
    private final List<SinkListener> listeners = new CopyOnWriteArrayList<>();

    public LogRouter(Plugin plugin){ this.plugin=plugin; this.suppressor=new Suppressor(plugin); }
    public void addListener(SinkListener l){ if (l!=null) listeners.add(l); }
    public void setListener(SinkListener l){ listeners.clear(); if (l!=null) listeners.add(l); }

    private FileLogger logger(String category){
        return loggers.computeIfAbsent(category, c -> new FileLogger(new File(plugin.getDataFolder(), "logs/"+c)));
    }
    private String today(){ return dayFmt.format(new Date()); }
    private String stamp(String m){ return "[" + new SimpleDateFormat("HH:mm:ss").format(new Date()) + "] " + m; }

    public void info(String msg){ write("info", msg); }
    public void warn(String msg){ write("warns", msg); }
    public void error(String msg){ write("errors", msg); }
    public void chat(String msg){ write("chat", msg); }
    public void command(String msg){ write("commands", msg); }
    public void console(String msg){ write("console", msg); }

    public void player(String player, String msg){
        FileLogger pl = new FileLogger(new File(plugin.getDataFolder(), "logs/players"));
        pl.append("player-"+player+"-"+today()+".log", stamp(msg));
        notifyListeners("players", msg);
    }

    public void write(String category, String msg){
        Suppressor.Result r = suppressor.filter(msg);
        if (r.drop) return;
        String file = "global-" + today() + ".log";
        logger(category).append(file, stamp(r.line));
        if ("errors".equals(category)) DiscordAlerter.maybeSend("errors", r.line);
        if ("warns".equals(category))  DiscordAlerter.maybeSend("warns",  r.line);
        if (r.summary != null) {
            logger("suppressed").append("suppressed-" + today() + ".log", stamp(r.summary));
        }
        notifyListeners(category, r.line);
    }

    private void notifyListeners(String category, String line){
        for (SinkListener l : listeners) try { l.onLogged(category, line); } catch (Throwable ignored){}
    }

    public void rotateAll(){
        ArchiveManager.archiveOldLogs(plugin.getDataFolder(), plugin.getConfig().getInt("logs.keep-days", 30));
    }


    // === PATCH: per-player aware logging ===
    private void writeForPlayer(String category, java.util.UUID uuid, String msg) {
        if (!plugin.getConfig().getBoolean("logs.split-by-player", true)) return;
        java.io.File base = new java.io.File(plugin.getDataFolder(), "logs/" + category + "/players/" + uuid);
        base.mkdirs();
        String file = "global-" + today() + ".log";
        new FileLogger(base).append(file, stamp(msg));
    }

    public void chat(java.util.UUID uuid, String msg) {
        write("chat", msg);
        writeForPlayer("chat", uuid, msg);
    }
    public void command(java.util.UUID uuid, String msg) {
        write("commands", msg);
        writeForPlayer("commands", uuid, msg);
    }
    public void economy(java.util.UUID uuid, String msg) {
        write("economy", msg);
        writeForPlayer("economy", uuid, msg);
    }
    public void combat(java.util.UUID uuid, String msg) {
        write("combat", msg);
        writeForPlayer("combat", uuid, msg);
    }
    public void inventory(java.util.UUID uuid, String msg) {
        write("inventory", msg);
        writeForPlayer("inventory", uuid, msg);
    }


    // === PATCHED3: Write also to logs/<category>/players/<uuid>/YYYY-MM-DD.log ===
    private void writeForPlayerUUID(String category, java.util.UUID uuid, String msg) {
        if (!plugin.getConfig().getBoolean("logs.split-by-player", true)) return;
        java.io.File base = new java.io.File(plugin.getDataFolder(), "logs/" + category + "/players/" + uuid.toString());
        base.mkdirs();
        String file = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date()) + ".log";
        new FileLogger(base).append(file, stamp(msg));
    }
}
