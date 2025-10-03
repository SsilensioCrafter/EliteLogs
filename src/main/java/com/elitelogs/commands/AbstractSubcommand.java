package com.elitelogs.commands;

import com.elitelogs.EliteLogsPlugin;
import com.elitelogs.localization.Lang;

public abstract class AbstractSubcommand implements Subcommand {
    protected final EliteLogsPlugin plugin;
    protected final Lang lang;

    protected AbstractSubcommand(EliteLogsPlugin plugin, Lang lang) {
        this.plugin = plugin;
        this.lang = lang;
    }

    @Override
    public String permission() {
        return "elogs.admin";
    }
}
