package com.elitelogs.utils;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.Plugin;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;

public class MetricsCollector {
    private final Plugin plugin;
    private final LogRouter router;
    private BukkitTask task;
    private double currentTPS = 20.0;

    public MetricsCollector(Plugin plugin, LogRouter router){ this.plugin = plugin; this.router = router; }

    public void start(){
        int interval = plugin.getConfig().getInt("metrics.interval-seconds", 60);
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            double cpu = getCpuLoadPercent();
            long used = getHeapUsedMB();
            long max = getHeapMaxMB();
            int online = Bukkit.getOnlinePlayers().size();
            router.write("stats", String.format("[metrics] TPS=%.2f CPU=%.1f%% HEAP=%d/%dMB ONLINE=%d",
                    currentTPS, cpu, used, max, online));
        }, 20L, interval * 20L);
    }
    public void stop(){ if (task != null) task.cancel(); }

    public double getCurrentTPS(){ return currentTPS; }
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
