package com.elitelogs.commands;

import com.elitelogs.EliteLogsPlugin;
import com.elitelogs.localization.Lang;
import com.elitelogs.reporting.Exporter;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.elitelogs.localization.Lang.colorize;

public class ExportSubcommand extends AbstractSubcommand {
    public ExportSubcommand(EliteLogsPlugin plugin, Lang lang) {
        super(plugin, lang);
    }

    @Override
    public String name() {
        return "export";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        String mode = (args.length > 0) ? args[0].toLowerCase(java.util.Locale.ROOT) : "today";
        try {
            File out;
            switch (mode) {
                case "full":
                    out = Exporter.exportFull(plugin.getDataFolder());
                    break;
                case "last-crash":
                    out = Exporter.exportLastCrash(plugin.getDataFolder());
                    break;
                default:
                    out = Exporter.exportToday(plugin.getDataFolder());
                    break;
            }
            sender.sendMessage(colorize(lang.get("command-export-success").replace("{path}", out.getAbsolutePath())));
        } catch (Exception e) {
            sender.sendMessage(colorize(lang.get("command-export-error").replace("{message}", String.valueOf(e.getMessage()))));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("today", "full", "last-crash");
        }
        return Collections.emptyList();
    }
}
