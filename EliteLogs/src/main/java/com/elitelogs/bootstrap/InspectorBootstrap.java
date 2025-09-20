package com.elitelogs.bootstrap;

import com.elitelogs.EliteLogsPlugin;
import com.elitelogs.inspector.Inspector;
import com.elitelogs.localization.Lang;

public class InspectorBootstrap {
    private final EliteLogsPlugin plugin;
    private final Lang lang;
    private Inspector inspector;

    public InspectorBootstrap(EliteLogsPlugin plugin, Lang lang) {
        this.plugin = plugin;
        this.lang = lang;
    }

    public Inspector start() {
        this.inspector = new Inspector(plugin, lang);
        if (plugin.getConfig().getBoolean("inspector.enabled", true)) {
            plugin.getServer().getScheduler().runTaskLater(plugin, inspector::runAll, 20L * 10);
        }
        return inspector;
    }

    public Inspector getInspector() {
        return inspector;
    }
}
