package com.elitelogs.api.provider;

import com.elitelogs.api.ApiLogBuffer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class BufferLogProvider implements LogDataProvider {
    private final ApiLogBuffer buffer;

    public BufferLogProvider(ApiLogBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public String getName() {
        return "buffer";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public List<String> listCategories() {
        Set<String> categories = buffer.getCategories();
        if (categories.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>(categories);
        Collections.sort(list);
        return list;
    }

    @Override
    public List<Map<String, Object>> fetch(String category, int limit) {
        List<String> lines = buffer.getRecent(category, limit);
        if (lines.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> records = new ArrayList<>(lines.size());
        for (String line : lines) {
            records.add(toRecord(category, line));
        }
        return records;
    }

    @Override
    public List<Map<String, Object>> search(String category, String query, int limit) {
        String needle = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
        if (needle.isEmpty()) {
            return fetch(category, limit);
        }
        List<String> lines = buffer.getRecent(category, buffer.getCapacity());
        if (lines.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> matches = new ArrayList<>();
        for (int i = lines.size() - 1; i >= 0 && matches.size() < limit; i--) {
            String line = lines.get(i);
            if (line != null && line.toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(toRecord(category, line));
            }
        }
        Collections.reverse(matches);
        return matches;
    }

    private Map<String, Object> toRecord(String category, String line) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("category", category);
        record.put("source", getName());
        record.put("line", line);
        Instant parsed = parseTimestamp(line);
        if (parsed != null) {
            record.put("occurredAt", parsed.toString());
        }
        record.put("message", stripTimestamp(line));
        return record;
    }

    private String stripTimestamp(String line) {
        if (line == null) {
            return "";
        }
        int close = line.indexOf(']');
        if (line.startsWith("[") && close > 0) {
            return line.substring(close + 1).trim();
        }
        return line;
    }

    private Instant parseTimestamp(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (!trimmed.startsWith("[")) {
            return null;
        }
        int closing = trimmed.indexOf(']');
        if (closing <= 1) {
            return null;
        }
        String payload = trimmed.substring(1, closing);
        try {
            if (payload.length() == 8) { // HH:mm:ss
                LocalTime time = LocalTime.parse(payload, DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT));
                return time.atDate(LocalDate.now()).atZone(ZoneId.systemDefault()).toInstant();
            }
            if (payload.length() >= 19 && payload.contains("T")) {
                return Instant.parse(payload);
            }
            if (payload.length() >= 19 && payload.contains(" ")) {
                LocalDateTime dateTime = LocalDateTime.parse(payload, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT));
                return dateTime.atZone(ZoneId.systemDefault()).toInstant();
            }
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }
}
