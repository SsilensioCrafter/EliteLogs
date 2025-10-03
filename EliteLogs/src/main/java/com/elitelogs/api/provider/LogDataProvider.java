package com.elitelogs.api.provider;

import java.util.List;
import java.util.Map;

public interface LogDataProvider {
    String getName();

    boolean isAvailable();

    List<String> listCategories();

    List<Map<String, Object>> fetch(String category, int limit);

    default List<Map<String, Object>> search(String category, String query, int limit) {
        return fetch(category, limit);
    }
}
