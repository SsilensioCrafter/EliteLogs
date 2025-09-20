package com.elitelogs.bootstrap;

import com.elitelogs.EliteLogsPlugin;
import com.elitelogs.integration.VaultEconomyTracker;
import com.elitelogs.logging.LogRouter;
import com.elitelogs.metrics.MetricsCollector;
import com.elitelogs.metrics.Watchdog;
import com.elitelogs.players.PlayerTracker;

public class MetricsBootstrap {
    private final EliteLogsPlugin plugin;
    private MetricsCollector metricsCollector;
    private VaultEconomyTracker economyTracker;
    private Watchdog watchdog;

    public MetricsBootstrap(EliteLogsPlugin plugin) {
        this.plugin = plugin;
    }

    public MetricsServices start(LogRouter router, PlayerTracker tracker) {
        this.metricsCollector = new MetricsCollector(plugin, router);
        if (plugin.getConfig().getBoolean("metrics.enabled", true)) {
            metricsCollector.start();
        }
        this.economyTracker = new VaultEconomyTracker(plugin, router, tracker);
        if (plugin.getConfig().getBoolean("logs.types.economy", true)) {
            economyTracker.start();
        }
        this.watchdog = new Watchdog(plugin, router, metricsCollector);
        if (plugin.getConfig().getBoolean("watchdog.enabled", true)) {
            watchdog.start();
        }
        return new MetricsServices(metricsCollector, economyTracker, watchdog);
    }

    public void shutdown() {
        if (metricsCollector != null) {
            metricsCollector.stop();
        }
        if (economyTracker != null) {
            economyTracker.stop();
        }
        if (watchdog != null) {
            watchdog.stop();
        }
    }

    public MetricsCollector getMetricsCollector() {
        return metricsCollector;
    }

    public VaultEconomyTracker getEconomyTracker() {
        return economyTracker;
    }

    public Watchdog getWatchdog() {
        return watchdog;
    }

    public static final class MetricsServices {
        private final MetricsCollector metricsCollector;
        private final VaultEconomyTracker economyTracker;
        private final Watchdog watchdog;

        private MetricsServices(MetricsCollector metricsCollector, VaultEconomyTracker economyTracker, Watchdog watchdog) {
            this.metricsCollector = metricsCollector;
            this.economyTracker = economyTracker;
            this.watchdog = watchdog;
        }

        public MetricsCollector metricsCollector() {
            return metricsCollector;
        }

        public VaultEconomyTracker economyTracker() {
            return economyTracker;
        }

        public Watchdog watchdog() {
            return watchdog;
        }
    }
}
