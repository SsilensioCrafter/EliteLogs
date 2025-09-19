package com.elitelogs.utils;

import java.util.Locale;
import java.util.logging.Logger;

public final class AsciiBanner {
    private static final String[] BANNER_LINES = {
            "██╗      ██████╗   ██████╗ ███████╗",
            "██║     ██╔═══██╗ ██╔════╝ ██╔════╝",
            "██║     ██║   ██║ ██║  ███╗███████╗",
            "██║     ██║   ██║ ██║   ██║╚════██║",
            "███████╗╚██████╔╝ ╚██████╔╝███████║",
            "╚══════╝ ╚═════╝   ╚═════╝ ╚══════╝"
    };

    private AsciiBanner() {
    }

    public static void print(Logger log, String version, String style, String color, boolean showVersion) {
        if (isDisabled(style)) {
            return;
        }

        String colorCode = resolveColor(color);
        String reset = colorCode.isEmpty() ? "" : Lang.colorize("&r");

        log.info("");
        for (String line : BANNER_LINES) {
            log.info(colorCode + line + reset);
        }
        if (showVersion) {
            log.info(colorCode + "EliteLogs v" + version + " — logging & monitoring system" + reset);
        }
        log.info("");
    }

    private static boolean isDisabled(String style) {
        if (style == null) {
            return false;
        }
        String key = style.trim().toLowerCase(Locale.ROOT);
        return key.equals("none") || key.equals("off") || key.equals("disabled");
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
            case "green":
                return Lang.colorize("&a");
            case "aqua":
                return Lang.colorize("&b");
            case "yellow":
                return Lang.colorize("&e");
            case "red":
                return Lang.colorize("&c");
            case "blue":
                return Lang.colorize("&9");
            case "light_purple":
            case "pink":
                return Lang.colorize("&d");
            case "white":
                return Lang.colorize("&f");
            case "gray":
            case "grey":
                return Lang.colorize("&7");
            default:
                if (value.startsWith("&") || value.startsWith("§")) {
                    return Lang.colorize(value);
                }
                return "";
        }
    }
}
