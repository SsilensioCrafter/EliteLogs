package com.elitelogs.api.provider;

import com.elitelogs.logging.LogRouter;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FileLogProvider implements LogDataProvider {
    private final Plugin plugin;
    private final LogRouter router;
    private final File root;

    public FileLogProvider(Plugin plugin, LogRouter router, String rootPath) {
        this.plugin = plugin;
        this.router = router;
        if (rootPath == null || rootPath.trim().isEmpty()) {
            rootPath = "logs";
        }
        File candidate = new File(rootPath);
        if (!candidate.isAbsolute()) {
            candidate = new File(plugin.getDataFolder(), rootPath);
        }
        this.root = candidate;
    }

    @Override
    public String getName() {
        return "files";
    }

    @Override
    public boolean isAvailable() {
        return root.exists() && root.isDirectory();
    }

    @Override
    public List<String> listCategories() {
        Set<String> categories = new LinkedHashSet<>();
        categories.addAll(router.getActiveCategories());
        if (isAvailable()) {
            File[] children = root.listFiles(File::isDirectory);
            if (children != null) {
                for (File child : children) {
                    categories.add(child.getName());
                }
            }
        }
        List<String> list = new ArrayList<>(categories);
        Collections.sort(list);
        return list;
    }

    @Override
    public List<Map<String, Object>> fetch(String category, int limit) {
        File target = locateLatestFile(category);
        if (target == null) {
            return Collections.emptyList();
        }
        List<String> lines = tail(target, limit);
        if (lines.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> records = new ArrayList<>(lines.size());
        for (String line : lines) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("category", category);
            record.put("source", getName());
            record.put("file", target.getName());
            record.put("line", line);
            record.put("message", line);
            records.add(record);
        }
        return records;
    }

    @Override
    public List<Map<String, Object>> search(String category, String query, int limit) {
        String needle = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
        if (needle.isEmpty()) {
            return fetch(category, limit);
        }
        File target = locateLatestFile(category);
        if (target == null) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> matches = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(target, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.toLowerCase(Locale.ROOT).contains(needle)) {
                    Map<String, Object> record = new LinkedHashMap<>();
                    record.put("category", category);
                    record.put("source", getName());
                    record.put("file", target.getName());
                    record.put("line", line);
                    record.put("message", line);
                    matches.add(record);
                    if (matches.size() >= limit) {
                        break;
                    }
                }
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("[EliteLogs] Failed to search log file " + target.getName() + ": " + ex.getMessage());
        }
        return matches;
    }

    private File locateLatestFile(String category) {
        if (category == null || category.trim().isEmpty()) {
            return null;
        }
        File categoryFolder = new File(root, category);
        if (!categoryFolder.exists() || !categoryFolder.isDirectory()) {
            return null;
        }
        File[] files = categoryFolder.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                String name = pathname.getName().toLowerCase(Locale.ROOT);
                return pathname.isFile() && (name.endsWith(".log") || name.endsWith(".txt") || name.endsWith(".yml"));
            }
        });
        if (files == null || files.length == 0) {
            return null;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        return files[0];
    }

    private List<String> tail(File file, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<>(limit);
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long fileLength = raf.length();
            long pointer = fileLength - 1;
            StringBuilder builder = new StringBuilder();
            while (pointer >= 0 && lines.size() < limit) {
                raf.seek(pointer--);
                int read = raf.read();
                if (read == '\n') {
                    if (builder.length() > 0) {
                        lines.add(builder.reverse().toString());
                        builder.setLength(0);
                    }
                } else if (read != '\r') {
                    builder.append((char) read);
                }
            }
            if (builder.length() > 0 && lines.size() < limit) {
                lines.add(builder.reverse().toString());
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("[EliteLogs] Failed to read log file " + file.getName() + ": " + ex.getMessage());
        }
        Collections.reverse(lines);
        return lines;
    }
}
