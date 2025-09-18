package com.elitelogs.utils;

import org.bukkit.plugin.Plugin;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class DiscordAlerter {
    private static Plugin plugin;
    private static String webhook;
    private static boolean enabled;
    private static int rateLimitSeconds = 10;
    private static final Map<String, Long> lastSend = new ConcurrentHashMap<>();

    public static void init(Plugin pl){
        plugin = pl;
        enabled = plugin.getConfig().getBoolean("discord.enabled", false);
        webhook = plugin.getConfig().getString("discord.webhook-url", "");
        rateLimitSeconds = plugin.getConfig().getInt("discord.rate-limit-seconds", 10);
    }

    public static void maybeSend(String channel, String content){
        if (!enabled || webhook == null || webhook.isEmpty()) return;
        boolean channelAllowed = plugin.getConfig().getBoolean("discord.send." + channel, true);
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
            String json = "{\"content\":\"" + content.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
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
}
