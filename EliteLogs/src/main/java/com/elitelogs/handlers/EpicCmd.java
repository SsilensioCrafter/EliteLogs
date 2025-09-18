package com.elitelogs.handlers;

import com.elitelogs.EliteLogsPlugin;
import com.elitelogs.inspector.Inspector;
import com.elitelogs.utils.*;
import org.bukkit.command.*;
import java.io.File;
import java.util.*;

public class EpicCmd implements CommandExecutor, TabCompleter {
    private final EliteLogsPlugin plugin;
    private final Lang lang;
    private final LogRouter router;
    private final MetricsCollector metrics;
    private final Inspector inspector;
    private final SessionManager sessions;
    private final List<String> root = Arrays.asList("help","reload","version","export","inspector","logs","metrics","session","rotate");

    public EpicCmd(EliteLogsPlugin plugin, Lang lang, LogRouter router, MetricsCollector metrics, Inspector inspector, SessionManager sessions) {
        this.plugin = plugin; this.lang = lang; this.router = router; this.metrics = metrics; this.inspector = inspector; this.sessions = sessions;
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) return help(sender);
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "reload":
                plugin.reloadConfig(); lang.load(); DiscordAlerter.init(plugin);
                sender.sendMessage(Lang.colorize("&a[EliteLogs]&f Конфиг/язык перезагружены.")); return true;
            case "version":
                sender.sendMessage(Lang.colorize("&dEliteLogs &7v" + plugin.getDescription().getVersion())); return true;
            case "metrics":
                if (args.length > 1 && args[1].equalsIgnoreCase("now")) {
                    sender.sendMessage(Lang.colorize("&eTPS: &f" + String.format(java.util.Locale.US,"%.2f", metrics.getCurrentTPS())));
                    return true;
                }
                sender.sendMessage(Lang.colorize("&7Используй: /elogs metrics now")); return true;
            case "rotate":
                router.rotateAll(); sender.sendMessage(Lang.colorize("&aАрхивация старых логов запущена.")); return true;
            case "export":
                String mode = (args.length > 1) ? args[1].toLowerCase(java.util.Locale.ROOT) : "today";
                try {
                    File out;
                    switch (mode){
                        case "full": out = Exporter.exportFull(plugin.getDataFolder()); break;
                        case "last-crash": out = Exporter.exportLastCrash(plugin.getDataFolder()); break;
                        default: out = Exporter.exportToday(plugin.getDataFolder());
                    }
                    sender.sendMessage(Lang.colorize("&aZIP: &f" + out.getAbsolutePath()));
                } catch (Exception e){ sender.sendMessage(Lang.colorize("&cОшибка экспорта: &f" + e.getMessage())); }
                return true;
            case "inspector":
                inspector.runAll();
                sender.sendMessage(Lang.colorize("&a[Inspector]&f отчёт обновлён.")); return true;
            case "logs":
                if (args.length > 2 && args[1].equalsIgnoreCase("toggle")){
                    String type = args[2].toLowerCase(java.util.Locale.ROOT);
                    String path = "logs.types."+type;
                    boolean v = plugin.getConfig().getBoolean(path, true);
                    plugin.getConfig().set(path, !v); plugin.saveConfig();
                    sender.sendMessage(Lang.colorize("&aЛог &f"+type+" &a→ " + (!v)));
                    return true;
                } else {
                    sender.sendMessage(Lang.colorize("&7Категории: info,warns,errors,chat,commands,players,combat,inventory,economy,stats,console,suppressed"));
                    sender.sendMessage(Lang.colorize("&7Toggle: /elogs logs toggle <type>"));
                    return true;
                }
            case "session":
                sender.sendMessage(Lang.colorize("&7Session: автосохранение каждые N минут и при выключении.")); return true;
        }
        return help(sender);
    }

    private boolean help(CommandSender s) {
        s.sendMessage(Lang.colorize(""));
        s.sendMessage(Lang.colorize(plugin.lang().get("help-header")));
        for (String line : plugin.lang().getList("help-commands")) s.sendMessage(Lang.colorize("  " + line));
        s.sendMessage(Lang.colorize(plugin.lang().get("help-footer")));
        return true;
    }

    @Override public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(root, args[0]);
        if (args.length == 2) {
            switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
                case "inspector": return java.util.Arrays.asList("all","plugins","mods","configs","garbage","server");
                case "logs": return java.util.Arrays.asList("toggle");
                case "metrics": return java.util.Arrays.asList("now");
                case "export": return java.util.Arrays.asList("today","full","last-crash");
                case "session": return java.util.Arrays.asList("last");
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("logs") && args[1].equalsIgnoreCase("toggle")) {
            return java.util.Arrays.asList("info","warns","errors","chat","commands","players","combat","inventory","economy","stats","console","suppressed");
        }
        return java.util.Collections.emptyList();
    }

    private java.util.List<String> filter(java.util.List<String> options, String prefix){
        String p = prefix.toLowerCase(java.util.Locale.ROOT);
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String o : options) if (o.startsWith(p)) out.add(o);
        return out;
    }
}
