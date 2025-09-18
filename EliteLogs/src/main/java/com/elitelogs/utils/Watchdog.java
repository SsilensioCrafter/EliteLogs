package com.elitelogs.utils;

import com.elitelogs.EliteLogsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

public class Watchdog implements LogRouter.SinkListener {
    private final EliteLogsPlugin plugin;
    private final LogRouter router;
    private final MetricsCollector metrics;
    private BukkitTask task;
    private AtomicInteger errorsWindow = new AtomicInteger();

    public Watchdog(EliteLogsPlugin plugin, LogRouter router, MetricsCollector metrics) {
        this.plugin = plugin;
        this.router = router;
        this.metrics = metrics;
        router.addListener(this);
    }

    public void start() {
        stop();
        int periodTicks = 20 * 5; // каждые 5 секунд
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, periodTicks, periodTicks);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void tick() {
        double tps = metrics.getCurrentTPS();
        int errorThreshold = plugin.getConfig().getInt("watchdog.error-threshold", 50);
        double tpsThreshold = plugin.getConfig().getDouble("watchdog.tps-threshold", 5.0);
        int errorCount = errorsWindow.getAndSet(0);

        if (tps < tpsThreshold || errorCount > errorThreshold) {
            router.warn("[Watchdog] Triggered: TPS=" + tps + " errors/5s=" + errorCount);

            if (plugin.getConfig().getBoolean("watchdog.actions.run-inspector", true)) {
                // можно дернуть инспектор командой или API - здесь оставлен хук
            }

            if (plugin.getConfig().getBoolean("watchdog.actions.create-crash-report", true)) {
                String ts = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
                File out = new File(plugin.getDataFolder(),
                        "exports/inspector-report-CRASH-" + ts + ".zip");
                try {
                    Exporter.exportToday(plugin.getDataFolder());
                    router.warn("[Watchdog] Crash report prepared: " + out.getAbsolutePath());
                    if (plugin.getConfig().getBoolean("watchdog.actions.discord-alert", true)) {
                        DiscordAlerter.maybeSend("watchdog", "Crash report prepared: " + out.getName());
                    }
                } catch (IOException e) {
                    router.error("[Watchdog] Export failed: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void onLogged(String category, String line) {
        if ("errors".equals(category)) errorsWindow.incrementAndGet();
    }
}
