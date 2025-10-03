package com.elitelogs.commands;

import com.elitelogs.EliteLogsPlugin;
import com.elitelogs.inspector.Inspector;
import com.elitelogs.localization.Lang;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.elitelogs.localization.Lang.colorize;

public class InspectorSubcommand extends AbstractSubcommand {
    private final Inspector inspector;

    public InspectorSubcommand(EliteLogsPlugin plugin, Lang lang, Inspector inspector) {
        super(plugin, lang);
        this.inspector = inspector;
    }

    @Override
    public String name() {
        return "inspector";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        inspector.runAll();
        sender.sendMessage(colorize(lang.get("command-inspector-updated")));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("all", "plugins", "mods", "configs", "garbage", "server");
        }
        return Collections.emptyList();
    }
}
