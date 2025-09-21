package com.elitelogs.metrics;

import com.elitelogs.EliteLogsPlugin;
import com.elitelogs.inspector.Inspector;
import com.elitelogs.integration.DiscordAlerter;
import com.elitelogs.localization.Lang;
import com.elitelogs.logging.LogRouter;
import com.elitelogs.reporting.Exporter;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class Watchdog implements LogRouter.SinkListener {
    public static final int SAMPLE_INTERVAL_SECONDS = 5;
    private static final int SAMPLE_PERIOD_TICKS = SAMPLE_INTERVAL_SECONDS * 20;
    private static final String REASON_TPS = "TPS_BELOW_THRESHOLD";
    private static final String REASON_ERRORS = "ERRORS_ABOVE_THRESHOLD";

    private final EliteLogsPlugin plugin;
    private final LogRouter router;
    private final MetricsCollector metrics;
    private final AtomicInteger errorsWindow = new AtomicInteger();
    private final AtomicInteger triggerCount = new AtomicInteger();

    private BukkitTask task;
    private volatile boolean running;
    private volatile long lastCheckMillis;
    private volatile double lastCheckTps;
    private volatile int lastCheckErrors;
    private volatile long lastTriggerMillis;
    private volatile double lastTriggerTps;
    private volatile int lastTriggerErrors;
    private volatile String lastTriggerReason;

    public Watchdog(EliteLogsPlugin plugin, LogRouter router, MetricsCollector metrics) {
        this.plugin = plugin;
        this.router = router;
        this.metrics = metrics;
        router.addListener(this);
    }

    public synchronized void start() {
        stop();
        errorsWindow.set(0);
        triggerCount.set(0);
        lastCheckMillis = 0L;
        lastCheckTps = 0.0;
        lastCheckErrors = 0;
        lastTriggerMillis = 0L;
        lastTriggerTps = 0.0;
        lastTriggerErrors = 0;
        lastTriggerReason = null;
        running = true;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, SAMPLE_PERIOD_TICKS, SAMPLE_PERIOD_TICKS);
    }

    public synchronized void stop() {
        running = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
        errorsWindow.set(0);
    }

    public boolean isRunning() {
        return running;
    }

    private void tick() {
        if (!running) {
            return;
        }
        double tps = metrics != null ? metrics.getCurrentTPS() : 0.0;
        int errorThreshold = plugin.getConfig().getInt("watchdog.error-threshold", 50);
        double tpsThreshold = plugin.getConfig().getDouble("watchdog.tps-threshold", 5.0);
        int errorCount = errorsWindow.getAndSet(0);

        long now = System.currentTimeMillis();
        lastCheckMillis = now;
        lastCheckTps = tps;
        lastCheckErrors = errorCount;

        boolean tpsLow = tps < tpsThreshold;
        boolean tooManyErrors = errorCount > errorThreshold;
        if (!tpsLow && !tooManyErrors) {
            return;
        }

        lastTriggerMillis = now;
        lastTriggerTps = tps;
        lastTriggerErrors = errorCount;
        String reason = buildReason(tpsLow, tooManyErrors);
        lastTriggerReason = reason;
        triggerCount.incrementAndGet();

        executeTriggerActions(tps, errorCount, reason);
    }

    private String buildReason(boolean tpsLow, boolean tooManyErrors) {
        if (tpsLow && tooManyErrors) {
            return REASON_TPS + "+" + REASON_ERRORS;
        }
        if (tpsLow) {
            return REASON_TPS;
        }
        if (tooManyErrors) {
            return REASON_ERRORS;
        }
        return null;
    }

    private void executeTriggerActions(double tps, int errorCount, String reason) {
        String suffix = reason != null ? " (" + reason + ")" : "";
        String triggerLine = String.format(Locale.ROOT, "[Watchdog] Triggered%s: TPS=%.2f errors/%ds=%d",
                suffix, tps, SAMPLE_INTERVAL_SECONDS, errorCount);
        router.warn(triggerLine);
        router.console(triggerLine);

        if (plugin.getConfig().getBoolean("watchdog.actions.run-inspector", true)) {
            runInspectorAsync();
        }

        if (plugin.getConfig().getBoolean("watchdog.actions.create-crash-report", true)) {
            prepareCrashReport();
        }
    }

    private void runInspectorAsync() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Inspector inspector = plugin.inspector();
                if (inspector != null) {
                    inspector.runAll();
                    return;
                }
                Lang lang = plugin.lang();
                if (lang != null) {
                    new Inspector(plugin, lang).runAll();
                } else {
                    router.warn("[Watchdog] Failed to run inspector: language bundle not ready");
                }
            } catch (Throwable t) {
                router.warn("[Watchdog] Failed to run inspector: " + t.getMessage());
            }
        });
    }

    private void prepareCrashReport() {
        String ts = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File crashTarget = new File(plugin.getDataFolder(),
                "exports/inspector-report-CRASH-" + ts + ".zip");
        try {
            File exported = Exporter.exportToday(plugin.getDataFolder());
            File finalFile = exported;
            if (!exported.equals(crashTarget)) {
                try {
                    Files.createDirectories(crashTarget.toPath().getParent());
                    Files.move(exported.toPath(), crashTarget.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    finalFile = crashTarget;
                } catch (IOException moveError) {
                    router.warn("[Watchdog] Failed to rename crash report to " + crashTarget.getName() + ": " + moveError.getMessage());
                }
            }
            String crashLine = "[Watchdog] Crash report prepared: " + finalFile.getAbsolutePath();
            router.warn(crashLine);
            router.console(crashLine);
            if (plugin.getConfig().getBoolean("watchdog.actions.discord-alert", true)) {
                DiscordAlerter.maybeSend("watchdog", "Crash report prepared: " + finalFile.getName());
            }
        } catch (IOException e) {
            router.error("[Watchdog] Export failed: " + e.getMessage());
        }
    }

    public WatchdogSnapshot snapshot() {
        return new WatchdogSnapshot(
                running,
                SAMPLE_INTERVAL_SECONDS,
                lastCheckMillis,
                lastCheckTps,
                lastCheckErrors,
                lastTriggerMillis,
                lastTriggerTps,
                lastTriggerErrors,
                lastTriggerReason,
                triggerCount.get(),
                errorsWindow.get()
        );
    }

    @Override
    public void onLogged(String category, String line) {
        if (!running) {
            return;
        }
        if ("errors".equals(category)) {
            errorsWindow.incrementAndGet();
        }
    }

    public static final class WatchdogSnapshot {
        private final boolean running;
        private final int intervalSeconds;
        private final long lastCheckMillis;
        private final double lastCheckTps;
        private final int lastCheckErrors;
        private final long lastTriggerMillis;
        private final double lastTriggerTps;
        private final int lastTriggerErrors;
        private final String lastTriggerReason;
        private final int triggerCount;
        private final int pendingErrors;

        private WatchdogSnapshot(boolean running, int intervalSeconds, long lastCheckMillis, double lastCheckTps,
                                 int lastCheckErrors, long lastTriggerMillis, double lastTriggerTps,
                                 int lastTriggerErrors, String lastTriggerReason, int triggerCount, int pendingErrors) {
            this.running = running;
            this.intervalSeconds = intervalSeconds;
            this.lastCheckMillis = lastCheckMillis;
            this.lastCheckTps = lastCheckTps;
            this.lastCheckErrors = lastCheckErrors;
            this.lastTriggerMillis = lastTriggerMillis;
            this.lastTriggerTps = lastTriggerTps;
            this.lastTriggerErrors = lastTriggerErrors;
            this.lastTriggerReason = lastTriggerReason;
            this.triggerCount = triggerCount;
            this.pendingErrors = pendingErrors;
        }

        public boolean isRunning() {
            return running;
        }

        public int getIntervalSeconds() {
            return intervalSeconds;
        }

        public long getLastCheckMillis() {
            return lastCheckMillis;
        }

        public double getLastCheckTps() {
            return lastCheckTps;
        }

        public int getLastCheckErrors() {
            return lastCheckErrors;
        }

        public long getLastTriggerMillis() {
            return lastTriggerMillis;
        }

        public double getLastTriggerTps() {
            return lastTriggerTps;
        }

        public int getLastTriggerErrors() {
            return lastTriggerErrors;
        }

        public String getLastTriggerReason() {
            return lastTriggerReason;
        }

        public int getTriggerCount() {
            return triggerCount;
        }

        public int getPendingErrors() {
            return pendingErrors;
        }
    }
}
