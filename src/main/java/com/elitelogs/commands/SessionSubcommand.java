package com.elitelogs.commands;

import com.elitelogs.EliteLogsPlugin;
import com.elitelogs.localization.Lang;
import com.elitelogs.reporting.SessionManager;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

import static com.elitelogs.localization.Lang.colorize;

public class SessionSubcommand extends AbstractSubcommand {
    private final SessionManager sessions;

    public SessionSubcommand(EliteLogsPlugin plugin, Lang lang, SessionManager sessions) {
        super(plugin, lang);
        this.sessions = sessions;
    }

    @Override
    public String name() {
        return "session";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        sender.sendMessage(colorize(lang.get("command-session-info")));
        if (args.length > 0 && sessions != null && "last".equalsIgnoreCase(args[0])) {
            sessions.forceSnapshot();
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Collections.singletonList("last");
        }
        return Collections.emptyList();
    }
}
