package com.elitelogs.commands;

import com.elitelogs.EliteLogsPlugin;
import com.elitelogs.localization.Lang;
import com.elitelogs.logging.ArchiveManager;
import com.elitelogs.logging.LogRouter;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

import static com.elitelogs.localization.Lang.colorize;

public class RotateSubcommand extends AbstractSubcommand {
    private final LogRouter router;

    public RotateSubcommand(EliteLogsPlugin plugin, Lang lang, LogRouter router) {
        super(plugin, lang);
        this.router = router;
    }

    @Override
    public String name() {
        return "rotate";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        boolean force = args.length > 0 && ("force".equalsIgnoreCase(args[0]) || "all".equalsIgnoreCase(args[0]));
        String startKey = force ? "command-rotate-started-force" : "command-rotate-started";
        sender.sendMessage(colorize(lang.get(startKey)));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ArchiveManager.Result result = router.rotateAll(force);
            Bukkit.getScheduler().runTask(plugin, () -> respond(sender, force, result));
        });
        return true;
    }

    private void respond(CommandSender sender, boolean force, ArchiveManager.Result result) {
        if (result.getError() != null) {
            String msg = lang.get("command-rotate-error").replace("{message}", String.valueOf(result.getError().getMessage()));
            sender.sendMessage(colorize(msg));
            return;
        }
        if (result.isSkipped()) {
            sender.sendMessage(colorize(lang.get("command-rotate-skipped")));
            return;
        }
        String summary = lang.get("command-rotate-finished")
                .replace("{archived}", String.valueOf(result.getArchived()))
                .replace("{failed}", String.valueOf(result.getFailed()))
                .replace("{candidates}", String.valueOf(result.getCandidates()));
        sender.sendMessage(colorize(summary));
        if (!force && result.getCandidates() == 0) {
            String keep = String.valueOf(plugin.getConfig().getInt("logs.keep-days", 30));
            sender.sendMessage(colorize(lang.get("command-rotate-none").replace("{days}", keep)));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Collections.singletonList("force");
        }
        return Collections.emptyList();
    }
}
