package com.elitelogs.commands;

import com.elitelogs.localization.Lang;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.elitelogs.localization.Lang.colorize;

public class ApiKeySubcommand extends AbstractSubcommand {

    public ApiKeySubcommand(com.elitelogs.EliteLogsPlugin plugin, Lang lang) {
        super(plugin, lang);
    }

    @Override
    public String name() {
        return "apikey";
    }

    @Override
    public List<String> aliases() {
        return Collections.singletonList("token");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0 || "show".equalsIgnoreCase(args[0])) {
            String token = plugin.getApiToken();
            if (token.isEmpty()) {
                token = plugin.ensureApiToken(false);
                sender.sendMessage(colorize(lang.get("command-apikey-missing").replace("{token}", token)));
            } else {
                sender.sendMessage(colorize(lang.get("command-apikey-current").replace("{token}", token)));
            }
            return true;
        }
        if ("regenerate".equalsIgnoreCase(args[0]) || "rotate".equalsIgnoreCase(args[0])) {
            String token = plugin.regenerateApiToken();
            sender.sendMessage(colorize(lang.get("command-apikey-regenerated").replace("{token}", token)));
            return true;
        }
        if ("status".equalsIgnoreCase(args[0])) {
            String statusKey = plugin.getApiToken().isEmpty()
                    ? lang.get("command-apikey-status-missing")
                    : lang.get("command-apikey-status-configured");
            sender.sendMessage(colorize(lang.get("command-apikey-status").replace("{status}", statusKey)));
            return true;
        }
        sender.sendMessage(colorize(lang.get("command-apikey-usage")));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("show", "status", "regenerate");
        }
        return Collections.emptyList();
    }
}

