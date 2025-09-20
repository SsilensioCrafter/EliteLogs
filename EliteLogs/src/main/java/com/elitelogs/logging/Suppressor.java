package com.elitelogs.logging;

import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Suppressor {
    public static class Result {
        public final boolean drop;
        public final String line;
        public final String summary;
        public Result(boolean drop, String line, String summary){
            this.drop = drop; this.line = line; this.summary = summary;
        }
        public static Result drop(){ return new Result(true, null, null); }
        public static Result allow(String line){ return new Result(false, line, null); }
        public static Result allowWithSummary(String line, String s){ return new Result(false, line, s); }
    }

    private final Plugin plugin;
    private volatile boolean enabled = true;
    private volatile int spamLimit;
    private volatile List<String> filters = Collections.emptyList();
    private volatile int maxEntries = 10000;
    private volatile long ttlMillis = 300_000L;
    private final Map<String, RepeatEntry> repeats = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicBoolean cleaning = new java.util.concurrent.atomic.AtomicBoolean();
    private volatile long lastCleanup = System.currentTimeMillis();

    private static final class RepeatEntry {
        int count;
        long lastSeen;
    }

    public Suppressor(Plugin plugin){
        this.plugin = plugin;
        reload();
    }

    public Result filter(String category, String line){
        if (!enabled) {
            return Result.allow(line);
        }
        String key = category + "\u0000" + line;
        long now = System.currentTimeMillis();
        RepeatEntry entry = repeats.compute(key, (k, existing) -> {
            if (existing == null) {
                existing = new RepeatEntry();
            }
            existing.count++;
            existing.lastSeen = now;
            return existing;
        });
        cleanupIfNeeded(now);

        int limit = this.spamLimit;
        int n = entry.count;
        if (limit > 0 && n > limit) return Result.drop();
        if (limit > 0 && n == limit) {
            return Result.allowWithSummary(line + " (suppressed " + (n-1) + " repeats)",
                    "[suppressed][" + category + "] '" + line + "' x" + (n-1));
        }
        // чёрный список
        boolean match = false;
        List<String> filters = this.filters;
        for (String f : filters) { if (line.contains(f)) { match = true; break; } }
        return match ? Result.drop() : Result.allow(line);
    }

    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("suppressor.enabled", true);
        this.spamLimit = plugin.getConfig().getInt("suppressor.spam-limit", 1000);
        this.maxEntries = Math.max(0, plugin.getConfig().getInt("suppressor.cache-max-entries", 10000));
        int ttlSeconds = Math.max(0, plugin.getConfig().getInt("suppressor.cache-ttl-seconds", 300));
        this.ttlMillis = ttlSeconds > 0 ? ttlSeconds * 1000L : 0L;
        this.filters = Collections.unmodifiableList(new ArrayList<>(plugin.getConfig().getStringList("suppressor.filters")));
        repeats.clear();
        lastCleanup = System.currentTimeMillis();
    }

    private void cleanupIfNeeded(long now) {
        if (repeats.isEmpty()) {
            return;
        }
        boolean sizeExceeded = maxEntries > 0 && repeats.size() > maxEntries;
        boolean ttlExpired = ttlMillis > 0 && (now - lastCleanup) >= ttlMillis;
        if (!sizeExceeded && !ttlExpired) {
            return;
        }
        if (!cleaning.compareAndSet(false, true)) {
            return;
        }
        try {
            long cutoff = ttlMillis > 0 ? now - ttlMillis : Long.MIN_VALUE;
            repeats.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().lastSeen < cutoff);
            if (maxEntries > 0 && repeats.size() > maxEntries) {
                repeats.entrySet().removeIf(entry -> repeats.size() > maxEntries && entry.getValue() != null && entry.getValue().count <= 1);
            }
            lastCleanup = now;
        } finally {
            cleaning.set(false);
        }
    }
}
