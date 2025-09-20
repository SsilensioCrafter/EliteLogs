package com.elitelogs.bootstrap;

import com.elitelogs.EliteLogsPlugin;
import com.elitelogs.logging.LogRouter;
import com.elitelogs.reporting.SessionManager;

public class SessionBootstrap {
    private final EliteLogsPlugin plugin;
    private SessionManager sessionManager;

    public SessionBootstrap(EliteLogsPlugin plugin) {
        this.plugin = plugin;
    }

    public SessionManager start(LogRouter router) {
        this.sessionManager = new SessionManager(plugin, router);
        if (plugin.getConfig().getBoolean("sessions.enabled", true)) {
            sessionManager.begin();
        }
        return sessionManager;
    }

    public void shutdown() {
        if (sessionManager != null) {
            sessionManager.end();
        }
    }
}
