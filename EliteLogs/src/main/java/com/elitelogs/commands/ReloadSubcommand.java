package com.elitelogs.commands;

import com.elitelogs.EliteLogsPlugin;
import com.elitelogs.integration.DiscordAlerter;
import com.elitelogs.localization.Lang;
import com.elitelogs.logging.LogRouter;
import org.bukkit.command.CommandSender;

import static com.elitelogs.localization.Lang.colorize;

public class ReloadSubcommand extends AbstractSubcommand {
    private final LogRouter router;

    public ReloadSubcommand(EliteLogsPlugin plugin, Lang lang, LogRouter router) {
        super(plugin, lang);
        this.router = router;
    }

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        plugin.reloadConfig();
        lang.load();
        DiscordAlerter.init(plugin);
        router.reloadConfig();
        plugin.reloadApi();
        sender.sendMessage(colorize(lang.get("command-reload")));
        return true;
    }

    @Override
    public java.util.List<String> tabComplete(CommandSender sender, String[] args) {
        return java.util.Collections.emptyList();
    }
}
