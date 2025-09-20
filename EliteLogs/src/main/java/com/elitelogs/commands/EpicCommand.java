package com.elitelogs.commands;

import com.elitelogs.localization.Lang;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.elitelogs.localization.Lang.colorize;

public class EpicCommand implements CommandExecutor, TabCompleter {
    private final Map<String, Subcommand> registry = new HashMap<>();
    private final List<Subcommand> ordered = new ArrayList<>();
    private final Subcommand fallback;
    private final Lang lang;

    public EpicCommand(Lang lang, Subcommand fallback, List<Subcommand> subcommands) {
        this.lang = lang;
        this.fallback = fallback;
        register(fallback);
        for (Subcommand sub : subcommands) {
            register(sub);
        }
    }

    private void register(Subcommand subcommand) {
        ordered.add(subcommand);
        registry.put(subcommand.name().toLowerCase(Locale.ROOT), subcommand);
        for (String alias : subcommand.aliases()) {
            registry.put(alias.toLowerCase(Locale.ROOT), subcommand);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return fallback.execute(sender, new String[0]);
        }
        Subcommand sub = find(args[0]);
        if (sub == null) {
            sender.sendMessage(colorize(lang.get("command-unknown").replace("{input}", args[0])));
            return fallback.execute(sender, new String[0]);
        }
        if (!hasPermission(sender, sub)) {
            sender.sendMessage(colorize(lang.get("command-no-permission")));
            return true;
        }
        String[] remainder = Arrays.copyOfRange(args, 1, args.length);
        return sub.execute(sender, remainder);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            Set<String> options = new LinkedHashSet<>();
            for (Subcommand sub : ordered) {
                if (!hasPermission(sender, sub)) {
                    continue;
                }
                String name = sub.name();
                if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    options.add(name);
                }
                for (String aliasName : sub.aliases()) {
                    if (aliasName.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        options.add(aliasName);
                    }
                }
            }
            return new ArrayList<>(options);
        }
        Subcommand sub = find(args[0]);
        if (sub == null || !hasPermission(sender, sub)) {
            return Collections.emptyList();
        }
        String[] remainder = Arrays.copyOfRange(args, 1, args.length);
        return sub.tabComplete(sender, remainder);
    }

    private Subcommand find(String name) {
        if (name == null) {
            return null;
        }
        return registry.get(name.toLowerCase(Locale.ROOT));
    }

    private boolean hasPermission(CommandSender sender, Subcommand sub) {
        String permission = sub.permission();
        return permission == null || sender.hasPermission(permission);
    }
}
