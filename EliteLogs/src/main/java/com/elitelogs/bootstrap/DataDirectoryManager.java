package com.elitelogs.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class DataDirectoryManager {
    private static final List<String> FOLDERS = Arrays.asList(
            "logs/info", "logs/warns", "logs/errors", "logs/chat", "logs/commands", "logs/players",
            "logs/combat", "logs/inventory", "logs/economy", "logs/stats", "logs/console", "logs/rcon", "logs/suppressed",
            "reports/sessions", "reports/inspector",
            "archive", "exports", "lang"
    );

    private final JavaPlugin plugin;

    public DataDirectoryManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void ensureStructure() {
        File data = plugin.getDataFolder();
        if (!data.exists()) {
            //noinspection ResultOfMethodCallIgnored
            data.mkdirs();
        }
        for (String path : FOLDERS) {
            File folder = new File(data, path);
            if (!folder.exists()) {
                //noinspection ResultOfMethodCallIgnored
                folder.mkdirs();
            }
        }
    }

    public void logLastSessionSummary() {
        Logger logger = plugin.getLogger();
        try {
            File summaryFile = new File(plugin.getDataFolder(), "reports/sessions/last-session.yml");
            if (!summaryFile.exists()) {
                return;
            }
            logger.info("=== Last Session Summary ===");
            Map<String, String> summary = new LinkedHashMap<>();
            List<String> rawLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(summaryFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    rawLines.add(line);
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    int colon = trimmed.indexOf(':');
                    if (colon <= 0) {
                        continue;
                    }
                    String key = trimmed.substring(0, colon).trim();
                    String value = trimmed.substring(colon + 1).trim();
                    summary.put(key, stripYamlQuotes(value));
                }
            }
            if (!summary.isEmpty()) {
                summary.entrySet().stream().limit(12).forEach(entry ->
                        logger.info(entry.getKey() + ": " + entry.getValue()));
            } else {
                rawLines.stream().limit(12).forEach(logger::info);
            }
        } catch (Exception ignored) {
        }
    }

    private String stripYamlQuotes(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
