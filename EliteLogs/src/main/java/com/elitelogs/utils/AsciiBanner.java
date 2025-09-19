package com.elitelogs.utils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

public class AsciiBanner {
    private static final Map<String, String[]> STYLES = new HashMap<>();

    static {
        STYLES.put("block", new String[]{
                "██╗      ██████╗   ██████╗ ███████╗",
                "██║     ██╔═══██╗ ██╔════╝ ██╔════╝",
                "██║     ██║   ██║ ██║  ███╗███████╗",
                "██║     ██║   ██║ ██║   ██║╚════██║",
                "███████╗╚██████╔╝ ╚██████╔╝███████║",
                "╚══════╝ ╚═════╝   ╚═════╝ ╚══════╝"
        });
        STYLES.put("slant", new String[]{
                "   _____ _ _ _        __                    ",
                "  / ____| (_) |      / _|                   ",
                " | |    | |_| |_ ___| |_ ___  _ __   ___ ___",
                " | |    | | | __/ _ \  _/ _ \| '_ \\ / __/ _ \\",
                " | |____| | | ||  __/ || (_) | | | | (_|  __/",
                "  \\_____|_|_|\\__\\___|_| \\___/|_| |_|\\___\\___|"
        });
        STYLES.put("mini", new String[]{
                " ___ _ _     _              ",
                "| __| (_)_ _| |___ _  _ ___ ",
                "| _|| | | '_| / -_) || / -_)",
                "|_| |_|_|_| |_\\___|\\_,_\\___|"
        });
    }

    public static void print(Logger log, String version, String style, String color, boolean showVersion) {
        String[] lines = STYLES.getOrDefault(normalize(style), STYLES.get("block"));
        String colorCode = resolveColor(color);
        String reset = colorCode.isEmpty() ? "" : Lang.colorize("&r");

        log.info("");
        for (String line : lines) {
            log.info(colorCode + line + reset);
        }
        if (showVersion) {
            log.info(colorCode + "EliteLogs v" + version + " — logging & monitoring system" + reset);
        }
        log.info("");
    }

    private static String normalize(String style) {
        if (style == null) {
            return "block";
        }
        String key = style.trim().toLowerCase(Locale.ROOT);
        return STYLES.containsKey(key) ? key : "block";
    }

    private static String resolveColor(String color) {
        if (color == null) {
            return "";
        }
        String value = color.trim();
        if (value.isEmpty() || value.equalsIgnoreCase("default") || value.equalsIgnoreCase("none")) {
            return "";
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "green": return Lang.colorize("&a");
            case "aqua": return Lang.colorize("&b");
            case "yellow": return Lang.colorize("&e");
            case "red": return Lang.colorize("&c");
            case "blue": return Lang.colorize("&9");
            case "light_purple":
            case "pink": return Lang.colorize("&d");
            case "white": return Lang.colorize("&f");
            case "gray":
            case "grey": return Lang.colorize("&7");
            default:
                if (value.startsWith("&") || value.startsWith("§")) {
                    return Lang.colorize(value);
                }
                return "";
        }
    }
}
