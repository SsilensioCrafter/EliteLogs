package com.elitelogs.commands;

import com.elitelogs.EliteLogsPlugin;
import com.elitelogs.localization.Lang;
import org.bukkit.command.CommandSender;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.elitelogs.localization.Lang.colorize;

public class LogsSubcommand extends AbstractSubcommand {
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
        for (String category : resolveCategories()) {
            boolean enabled = plugin.getConfig().getBoolean("logs.types." + category, true);
            String state = enabled ? lang.get("command-logs-enabled") : lang.get("command-logs-disabled");
            sender.sendMessage(colorize(" §7- §f" + category + " §8→ " + state));
        }
        sender.sendMessage(colorize(lang.get("command-logs-toggle-hint")));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Collections.singletonList("toggle");
        }
        if (args.length == 2 && "toggle".equalsIgnoreCase(args[0])) {
            return new ArrayList<>(resolveCategories());
        }
        return Collections.emptyList();
    }

    private Set<String> resolveCategories() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("logs.types");
        if (section == null) {
            return Collections.emptySet();
        }
        Set<String> categories = new LinkedHashSet<>(section.getKeys(false));
        List<String> sorted = new ArrayList<>(categories);
        sorted.sort(Comparator.naturalOrder());
        return new LinkedHashSet<>(sorted);
    }
}
