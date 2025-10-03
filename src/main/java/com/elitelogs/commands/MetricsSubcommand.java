package com.elitelogs.commands;

import com.elitelogs.EliteLogsPlugin;
import com.elitelogs.localization.Lang;
import com.elitelogs.metrics.MetricsCollector;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

import static com.elitelogs.localization.Lang.colorize;

public class MetricsSubcommand extends AbstractSubcommand {
    private final MetricsCollector metrics;

    public MetricsSubcommand(EliteLogsPlugin plugin, Lang lang, MetricsCollector metrics) {
        super(plugin, lang);
        this.metrics = metrics;
    }

    @Override
    public String name() {
        return "metrics";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length > 0 && "now".equalsIgnoreCase(args[0])) {
            String tps = String.format(java.util.Locale.US, "%.2f", metrics.getCurrentTPS());
            sender.sendMessage(colorize(lang.get("command-metrics-now").replace("{tps}", tps)));
            return true;
        }
        sender.sendMessage(colorize(lang.get("command-metrics-usage")));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Collections.singletonList("now");
        }
        return Collections.emptyList();
    }
}
