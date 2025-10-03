package com.elitelogs.commands;

import com.elitelogs.EliteLogsPlugin;
import com.elitelogs.localization.Lang;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

import static com.elitelogs.localization.Lang.colorize;

public class HelpSubcommand extends AbstractSubcommand {

    public HelpSubcommand(EliteLogsPlugin plugin, Lang lang) {
        super(plugin, lang);
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public List<String> aliases() {
        return Collections.singletonList("?");
    }

    @Override
    public String permission() {
        return null;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        sender.sendMessage(colorize(""));
        sender.sendMessage(colorize(lang.get("help-header")));
        for (String line : lang.getList("help-commands")) {
            sender.sendMessage(colorize("  " + line));
        }
        sender.sendMessage(colorize(lang.get("help-footer")));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
