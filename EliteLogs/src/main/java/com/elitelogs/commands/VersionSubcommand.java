package com.elitelogs.commands;

import com.elitelogs.EliteLogsPlugin;
import com.elitelogs.localization.Lang;
import org.bukkit.command.CommandSender;

import static com.elitelogs.localization.Lang.colorize;

public class VersionSubcommand extends AbstractSubcommand {
    public VersionSubcommand(EliteLogsPlugin plugin, Lang lang) {
        super(plugin, lang);
    }

    @Override
    public String name() {
        return "version";
    }

    @Override
    public String permission() {
        return null;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        sender.sendMessage(colorize("&dEliteLogs &7v" + plugin.getDescription().getVersion()));
        return true;
    }

    @Override
    public java.util.List<String> tabComplete(CommandSender sender, String[] args) {
        return java.util.Collections.emptyList();
    }
}
