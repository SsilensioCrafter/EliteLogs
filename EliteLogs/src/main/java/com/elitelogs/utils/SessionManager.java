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

    public SessionManager(Plugin plugin, LogRouter router){
        this.plugin = plugin; this.router = router;
        router.addListener(this);
    }

    public void begin(){
        start = System.currentTimeMillis();
        int minutes = plugin.getConfig().getInt("sessions.autosave-minutes", 10);
        autosaveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::save, 20L*60*minutes, 20L*60*minutes);
    }

    public void end(){
        if (autosaveTask != null) autosaveTask.cancel();
        save();
        DiscordAlerter.maybeSend("sessions", "Session saved");
    }

    private void save(){
        long dur = System.currentTimeMillis()-start;
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        File folder = new File(plugin.getDataFolder(), "reports/sessions");
        folder.mkdirs();
        File f = new File(folder, date + ".txt");
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))){
            pw.println("Session date: " + date);
            pw.println("Uptime: " + dur/1000 + " sec");
            pw.println("Joins: " + joins.get());
            pw.println("Warns: " + warns.get());
            pw.println("Errors: " + errors.get());
        } catch (Exception ignored){}
        // PATCHED3: write also to logs/sessions/global
        saveToLogs(dur, date);
        try {
            File last = new File(folder, "last-session.txt");
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
        if ("warns".equals(category)) warns.incrementAndGet();
        if ("errors".equals(category)) errors.incrementAndGet();
        if ("info".equals(category) && line.contains("[join]")) joins.incrementAndGet();
    }


    // === PATCH: also save to logs/sessions/global ===
    private void saveToLogs(long dur, String date) {
        java.io.File logsGlobal = new java.io.File(plugin.getDataFolder(), "logs/sessions");
        logsGlobal.mkdirs();
        java.io.File g = new java.io.File(logsGlobal, date + ".txt");
        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.OutputStreamWriter(new java.io.FileOutputStream(g), java.nio.charset.StandardCharsets.UTF_8))){
            pw.println("Uptime=" + dur/1000 + "s joins=" + joins.get() + " warns=" + warns.get() + " errors=" + errors.get());
        } catch (Exception ignored){}
    }
}
