package com.elitelogs.utils;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

public class SessionManager implements LogRouter.SinkListener {
    private final Plugin plugin;
    private final LogRouter router;
    private long start;
    private AtomicInteger warns = new AtomicInteger();
    private AtomicInteger errors = new AtomicInteger();
    private AtomicInteger joins = new AtomicInteger();
    private BukkitTask autosaveTask;
    private volatile boolean running;

    public SessionManager(Plugin plugin, LogRouter router){
        this.plugin = plugin; this.router = router;
        router.addListener(this);
    }

    public synchronized void begin(){
        if (running) {
            return;
        }
        running = true;
        start = System.currentTimeMillis();
        warns.set(0);
        errors.set(0);
        joins.set(0);
        int minutes = plugin.getConfig().getInt("sessions.autosave-minutes", 10);
        if (minutes > 0) {
            long interval = 20L * 60 * minutes;
            autosaveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> save(false), interval, interval);
        }
    }

    public synchronized void end(){
        if (!running) {
            return;
        }
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        save(true);
        running = false;
        start = 0L;
        DiscordAlerter.maybeSend("sessions", "Session saved");
    }

    private void save(boolean finalSave){
        long startedAt = this.start;
        if (!finalSave && (!running || startedAt == 0L)) {
            return;
        }
        if (startedAt == 0L) {
            return;
        }
        long dur = System.currentTimeMillis()-start;
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        File folder = new File(plugin.getDataFolder(), "reports/sessions");
        folder.mkdirs();
        long uptimeSeconds = dur / 1000;
        int joinCount = joins.get();
        int warnCount = warns.get();
        int errorCount = errors.get();

        File f = new File(folder, date + ".yml");
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))){
            writeSessionYaml(pw, date, uptimeSeconds, joinCount, warnCount, errorCount);
        } catch (Exception ignored){}
        // PATCHED3: write also to logs/sessions/global
        saveToLogs(uptimeSeconds, date, joinCount, warnCount, errorCount);
        try {
            File last = new File(folder, "last-session.yml");
            try (InputStream in = new FileInputStream(f);
                 OutputStream out = new FileOutputStream(last)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
        } catch(Exception ignored){}
    }

    @Override
    public void onLogged(String category, String line) {
        if (!running) {
            return;
        }
        if ("warns".equals(category)) warns.incrementAndGet();
        if ("errors".equals(category)) errors.incrementAndGet();
        if ("info".equals(category) && line.contains("[join]")) joins.incrementAndGet();
    }


    // === PATCH: also save to logs/sessions/global ===
    private void saveToLogs(long uptimeSeconds, String date, int joinCount, int warnCount, int errorCount) {
        java.io.File logsGlobal = new java.io.File(plugin.getDataFolder(), "logs/sessions");
        logsGlobal.mkdirs();
        java.io.File g = new java.io.File(logsGlobal, date + ".yml");
        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.OutputStreamWriter(new java.io.FileOutputStream(g), java.nio.charset.StandardCharsets.UTF_8))){
            writeSessionYaml(pw, date, uptimeSeconds, joinCount, warnCount, errorCount);
        } catch (Exception ignored){}
    }

    private void writeSessionYaml(PrintWriter pw, String date, long uptimeSeconds, int joinCount, int warnCount, int errorCount) {
        pw.println("date: \"" + date + "\"");
        pw.println("uptime-seconds: " + uptimeSeconds);
        pw.println("joins: " + joinCount);
        pw.println("warns: " + warnCount);
        pw.println("errors: " + errorCount);
    }

    public boolean isRunning() {
        return running;
    }
}
