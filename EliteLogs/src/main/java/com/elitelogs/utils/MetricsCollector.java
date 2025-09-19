package com.elitelogs.utils;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

public class MetricsCollector {
    private final Plugin plugin;
    private final LogRouter router;
    private BukkitTask metricsTask;
    private BukkitTask samplerTask;
    private volatile double currentTPS = 20.0;
    private long sampleStartNanos;
    private int sampleTicks;
    private Method tpsAccessor;
    private boolean tpsAccessorResolved;

    public MetricsCollector(Plugin plugin, LogRouter router){ this.plugin = plugin; this.router = router; }

    public void start(){
        stop();
        startSampler();
        int interval = plugin.getConfig().getInt("metrics.interval-seconds", 60);
        metricsTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            double cpu = getCpuLoadPercent();
            long used = getHeapUsedMB();
            long max = getHeapMaxMB();
            int online = Bukkit.getOnlinePlayers().size();
            router.write("stats", String.format("[metrics] TPS=%.2f CPU=%.1f%% HEAP=%d/%dMB ONLINE=%d",
                    currentTPS, cpu, used, max, online));
        }, 20L, interval * 20L);
    }

    public void stop(){
        if (metricsTask != null) {
            metricsTask.cancel();
            metricsTask = null;
        }
        if (samplerTask != null) {
            samplerTask.cancel();
            samplerTask = null;
        }
    }

    public double getCurrentTPS(){ return currentTPS; }

    private void startSampler() {
        sampleStartNanos = System.nanoTime();
        sampleTicks = 0;
        samplerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::sampleTick, 1L, 1L);
    }

    private void sampleTick() {
        double serverTps = readServerTps();
        if (!Double.isNaN(serverTps)) {
            currentTPS = Math.min(20.0, serverTps);
            sampleStartNanos = System.nanoTime();
            sampleTicks = 0;
            return;
        }

        sampleTicks++;
        long elapsed = System.nanoTime() - sampleStartNanos;
        if (elapsed >= TimeUnit.SECONDS.toNanos(5)) {
            double seconds = elapsed / 1_000_000_000.0;
            if (seconds > 0) {
                currentTPS = Math.min(20.0, sampleTicks / seconds);
            }
            sampleTicks = 0;
            sampleStartNanos = System.nanoTime();
        }
    }

    private double readServerTps() {
        if (!tpsAccessorResolved) {
            try {
                tpsAccessor = Bukkit.getServer().getClass().getMethod("getTPS");
            } catch (Throwable ignored) {
                tpsAccessor = null;
            } finally {
                tpsAccessorResolved = true;
            }
        }
        if (tpsAccessor != null) {
            try {
                Object value = tpsAccessor.invoke(Bukkit.getServer());
                if (value instanceof double[]) {
                    double[] arr = (double[]) value;
                    if (arr.length > 0) {
                        return arr[0];
                    }
                }
            } catch (Throwable ignored) {}
        }
        return Double.NaN;
    }

    public long getHeapUsedMB(){
        MemoryMXBean m = ManagementFactory.getMemoryMXBean();
        return m.getHeapMemoryUsage().getUsed() / (1024*1024);
    }

    public long getHeapMaxMB(){
        MemoryMXBean m = ManagementFactory.getMemoryMXBean();
        return m.getHeapMemoryUsage().getMax() / (1024*1024);
    }

    public double getCpuLoadPercent(){
        try {
            OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
            double v = (double) os.getClass().getMethod("getSystemCpuLoad").invoke(os);
            if (v >= 0) return Math.round(v*1000.0)/10.0;
        } catch (Exception ignored){}
        return 0.0;
    }
}
