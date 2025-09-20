package com.elitelogs.reporting;

import com.elitelogs.integration.DiscordAlerter;
import com.elitelogs.logging.LogRouter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

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
        SessionSnapshot snapshot = new SessionSnapshot(date, uptimeSeconds, joinCount, warnCount, errorCount);
        File report = new File(folder, date + ".yml");
        writeSessionReport(report, snapshot);
        saveToLogs(snapshot);
        updateLastSessionSnapshot(report, new File(folder, "last-session.yml"));
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


    private void saveToLogs(SessionSnapshot snapshot) {
        File logsGlobal = new File(plugin.getDataFolder(), "logs/sessions");
        logsGlobal.mkdirs();
        File target = new File(logsGlobal, snapshot.getDate() + ".yml");
        writeSessionReport(target, snapshot);
    }

    private void writeSessionReport(File target, SessionSnapshot snapshot) {
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(target), StandardCharsets.UTF_8))) {
            YamlReportWriter yaml = new YamlReportWriter(pw);
            writeSessionYaml(yaml, snapshot);
            yaml.flush();
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "[EliteLogs] Failed to write session report " + target.getName(), ex);
        }
    }

    private void updateLastSessionSnapshot(File source, File destination) {
        if (source == null || !source.exists()) {
            return;
        }
        try {
            Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "[EliteLogs] Failed to update last session snapshot", ex);
        }
    }

    private void writeSessionYaml(YamlReportWriter yaml, SessionSnapshot snapshot) {
        yaml.scalar("date", snapshot.getDate());
        yaml.scalar("uptime-seconds", snapshot.getUptimeSeconds());
        yaml.scalar("joins", snapshot.getJoins());
        yaml.scalar("warns", snapshot.getWarns());
        yaml.scalar("errors", snapshot.getErrors());
    }

    public boolean isRunning() {
        return running;
    }

    public void forceSnapshot() {
        save(false);
    }

    private static final class SessionSnapshot {
        private final String date;
        private final long uptimeSeconds;
        private final int joins;
        private final int warns;
        private final int errors;

        private SessionSnapshot(String date, long uptimeSeconds, int joins, int warns, int errors) {
            this.date = date;
            this.uptimeSeconds = uptimeSeconds;
            this.joins = joins;
            this.warns = warns;
            this.errors = errors;
        }

        public String getDate() {
            return date;
        }

        public long getUptimeSeconds() {
            return uptimeSeconds;
        }

        public int getJoins() {
            return joins;
        }

        public int getWarns() {
            return warns;
        }

        public int getErrors() {
            return errors;
        }
    }
}
