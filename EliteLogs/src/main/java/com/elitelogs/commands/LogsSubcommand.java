package com.elitelogs.commands;

import com.elitelogs.EliteLogsPlugin;
import com.elitelogs.localization.Lang;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static com.elitelogs.localization.Lang.colorize;

public class LogsSubcommand extends AbstractSubcommand {
    private static final List<String> CATEGORIES = Arrays.asList(
            "warns", "errors", "chat", "commands", "players", "combat",
            "inventory", "economy", "stats", "console", "suppressed"
    );

    public LogsSubcommand(EliteLogsPlugin plugin, Lang lang) {
        super(plugin, lang);
    }

    @Override
    public String name() {
        return "logs";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length >= 2 && "toggle".equalsIgnoreCase(args[0])) {
            String type = args[1].toLowerCase(Locale.ROOT);
            String path = "logs.types." + type;
            boolean value = plugin.getConfig().getBoolean(path, true);
            plugin.getConfig().set(path, !value);
            plugin.saveConfig();
            sender.sendMessage(colorize(lang.get("command-logs-toggled")
                    .replace("{type}", type)
                    .replace("{value}", String.valueOf(!value))));
            return true;
        }
        sender.sendMessage(colorize(lang.get("command-logs-list")));
        sender.sendMessage(colorize(lang.get("command-logs-toggle-hint")));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Collections.singletonList("toggle");
        }
        if (args.length == 2 && "toggle".equalsIgnoreCase(args[0])) {
            return CATEGORIES;
        }
        return Collections.emptyList();
    }
}
