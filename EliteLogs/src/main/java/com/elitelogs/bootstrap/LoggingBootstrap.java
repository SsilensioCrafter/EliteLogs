package com.elitelogs.bootstrap;

import com.elitelogs.EliteLogsPlugin;
import com.elitelogs.logging.AsciiBanner;
import com.elitelogs.logging.ConsoleHook;
import com.elitelogs.logging.ConsoleTee;
import com.elitelogs.logging.LogRouter;
import com.elitelogs.players.PlayerTracker;

public class LoggingBootstrap {
    private final EliteLogsPlugin plugin;
    private ConsoleHook consoleHook;
    private ConsoleTee consoleTee;
    private LogRouter logRouter;

    public LoggingBootstrap(EliteLogsPlugin plugin) {
        this.plugin = plugin;
    }

    public LoggingServices start(PlayerTracker tracker) {
        if (plugin.getConfig().getBoolean("banner.enabled", true)) {
            AsciiBanner.print(
                    plugin.getLogger(),
                    plugin.getDescription().getVersion(),
                    plugin.getConfig().getString("banner.style", "block"),
                    plugin.getConfig().getString("banner.color", "default"),
                    plugin.getConfig().getBoolean("banner.show-version", true)
            );
        }
        this.logRouter = new LogRouter(plugin);
        logRouter.setPlayerTracker(tracker);
        this.consoleHook = new ConsoleHook(logRouter);
        consoleHook.hook();
        this.consoleTee = new ConsoleTee(logRouter);
        consoleTee.setPreferLog4j(consoleHook.isLog4jAttached());
        consoleTee.hook();
        return new LoggingServices(logRouter, consoleHook, consoleTee);
    }

    public void shutdown() {
        if (consoleTee != null) {
            consoleTee.unhook();
        }
        if (consoleHook != null) {
            consoleHook.unhook();
        }
        if (logRouter != null) {
            logRouter.shutdown();
        }
    }

    public static final class LoggingServices {
        private final LogRouter router;
        private final ConsoleHook hook;
        private final ConsoleTee tee;

        private LoggingServices(LogRouter router, ConsoleHook hook, ConsoleTee tee) {
            this.router = router;
            this.hook = hook;
            this.tee = tee;
        }

        public LogRouter router() {
            return router;
        }

        public ConsoleHook hook() {
            return hook;
        }

        public ConsoleTee tee() {
            return tee;
        }
    }
}
