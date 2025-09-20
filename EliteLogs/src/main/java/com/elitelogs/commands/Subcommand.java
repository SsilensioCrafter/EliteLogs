package com.elitelogs.commands;

import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

public interface Subcommand {
    String name();

    default List<String> aliases() {
        return Collections.emptyList();
    }

    default String permission() {
        return null;
    }

    boolean execute(CommandSender sender, String[] args);

    List<String> tabComplete(CommandSender sender, String[] args);
}
