package com.elitelogs.api.provider;

import com.elitelogs.logging.DatabaseLogWriter;
import com.elitelogs.logging.LogRouter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DatabaseLogProvider implements LogDataProvider {
    private final LogRouter router;

    public DatabaseLogProvider(LogRouter router) {
        this.router = router;
    }

    @Override
    public String getName() {
        return "database";
    }

    @Override
    public boolean isAvailable() {
        return router.getDatabaseWriter() != null;
    }

    @Override
    public List<String> listCategories() {
        DatabaseLogWriter writer = router.getDatabaseWriter();
        if (writer == null) {
            return Collections.emptyList();
        }
        return writer.listRegisteredCategories();
    }

    @Override
    public List<Map<String, Object>> fetch(String category, int limit) {
        DatabaseLogWriter writer = router.getDatabaseWriter();
        if (writer == null) {
            return Collections.emptyList();
        }
        List<DatabaseLogWriter.DbRecord> rows = writer.fetchRecentRecords(category, limit);
        return toRecords(rows);
    }

    @Override
    public List<Map<String, Object>> search(String category, String query, int limit) {
        DatabaseLogWriter writer = router.getDatabaseWriter();
        if (writer == null) {
            return Collections.emptyList();
        }
        List<DatabaseLogWriter.DbRecord> rows = writer.searchRecords(category, query, limit);
        return toRecords(rows);
    }

    private List<Map<String, Object>> toRecords(List<DatabaseLogWriter.DbRecord> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> records = new ArrayList<>(rows.size());
        for (DatabaseLogWriter.DbRecord row : rows) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("category", row.getCategory());
            record.put("source", getName());
            Instant occurred = row.getOccurredAt();
            if (occurred != null) {
                record.put("occurredAt", occurred.toString());
            }
            record.put("eventType", row.getEventType());
            record.put("message", row.getMessage());
            UUID uuid = row.getPlayerUuid();
            if (uuid != null) {
                record.put("playerUuid", uuid.toString());
            }
            if (row.getPlayerName() != null) {
                record.put("playerName", row.getPlayerName());
            }
            if (row.getTagsJson() != null) {
                record.put("tags", row.getTagsJson());
            }
            if (row.getContextJson() != null) {
                record.put("context", row.getContextJson());
            }
            records.add(record);
        }
        return records;
    }
}
