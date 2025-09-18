package com.elitelogs.utils;

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
    private volatile int spamLimit;
    private volatile List<String> filters = Collections.emptyList();
    private final Map<String,Integer> repeats = new ConcurrentHashMap<>();

    public Suppressor(Plugin plugin){
        this.plugin = plugin;
        reload();
    }

    public Result filter(String category, String line){
        String key = category + "\u0000" + line;
        // лимитер повторов
        int n = repeats.merge(key, 1, Integer::sum);
        int limit = this.spamLimit;
        if (n > limit) return Result.drop();
        if (n == limit) {
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
        this.spamLimit = plugin.getConfig().getInt("suppressor.spam-limit", 1000);
        this.filters = Collections.unmodifiableList(new ArrayList<>(plugin.getConfig().getStringList("suppressor.filters")));
        repeats.clear();
    }
}
