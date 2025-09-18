package com.elitelogs.utils;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class DiscordAlerter {
    private static Plugin plugin;
    private static String webhook;
    private static boolean enabled;
    private static int rateLimitSeconds = 10;
    private static final Map<String, Long> lastSend = new ConcurrentHashMap<>();
    private static volatile Map<String, Boolean> channelPermissions = Collections.emptyMap();

    public static void init(Plugin pl){
        plugin = pl;
        enabled = plugin.getConfig().getBoolean("discord.enabled", false);
        webhook = plugin.getConfig().getString("discord.webhook-url", "");
        rateLimitSeconds = plugin.getConfig().getInt("discord.rate-limit-seconds", 10);
        channelPermissions = loadChannelPermissions();
    }

    public static void maybeSend(String channel, String content){
        if (!enabled || webhook == null || webhook.isEmpty()) return;
        boolean channelAllowed = channelPermissions.getOrDefault(channel.toLowerCase(Locale.ROOT), true);
        if (!channelAllowed) return;

        long now = System.currentTimeMillis();
        long cooldown = rateLimitSeconds * 1000L;
        AtomicBoolean permitted = new AtomicBoolean(false);
        Long updated = lastSend.compute(channel, (key, prev) -> {
            if (prev == null || now - prev >= cooldown) {
                permitted.set(true);
                return now;
            }
            return prev;
        });
        if (!permitted.get() || updated == null || updated.longValue() != now) {
            return;
        }

        try {
            if (content.length() > 1800) content = content.substring(0, 1800);
            String json = "{\"content\":\"" + escape(content) + "\"}";
            URL url = new URL(webhook);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            conn.getInputStream().close();
        } catch (Exception ignored) {}
    }

    private static Map<String, Boolean> loadChannelPermissions() {
        if (plugin == null) {
            return Collections.emptyMap();
        }
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("discord.send");
        if (section == null) {
            return Collections.emptyMap();
        }
        Map<String, Boolean> map = new ConcurrentHashMap<>();
        for (String key : section.getKeys(false)) {
            map.put(key.toLowerCase(Locale.ROOT), section.getBoolean(key, true));
        }
        return Collections.unmodifiableMap(map);
    }

    private static String escape(String content) {
        StringBuilder sb = new StringBuilder(content.length() + 32);
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\u2028': sb.append("\\u2028"); break;
                case '\u2029': sb.append("\\u2029"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format(Locale.ROOT, "\\u%04X", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
