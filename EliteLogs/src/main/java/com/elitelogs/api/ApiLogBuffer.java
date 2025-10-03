package com.elitelogs.api;

import com.elitelogs.logging.LogRouter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ApiLogBuffer implements LogRouter.SinkListener {
    private final Map<String, CategoryBuffer> buffers = new ConcurrentHashMap<>();
    private final AtomicInteger capacity = new AtomicInteger(250);

    @Override
    public void onLogged(String category, String line) {
        if (category == null || line == null) {
            return;
        }
        CategoryBuffer buffer = buffers.computeIfAbsent(category, key -> new CategoryBuffer());
        buffer.add(line, capacity.get());
    }

    void setCapacity(int newCapacity) {
        int normalized = Math.max(1, newCapacity);
        capacity.set(normalized);
        for (CategoryBuffer buffer : buffers.values()) {
            buffer.trim(normalized);
        }
    }

    public int getCapacity() {
        return capacity.get();
    }

    public Set<String> getCategories() {
        return Collections.unmodifiableSet(buffers.keySet());
    }

    public List<String> getRecent(String category, int limit) {
        CategoryBuffer buffer = buffers.get(category);
        if (buffer == null) {
            return Collections.emptyList();
        }
        return buffer.snapshot(limit);
    }

    private static final class CategoryBuffer {
        private final Deque<String> deque = new ArrayDeque<>();

        synchronized void add(String line, int capacity) {
            deque.addLast(line);
            trim(capacity);
        }

        synchronized void trim(int capacity) {
            while (deque.size() > capacity) {
                deque.removeFirst();
            }
        }

        synchronized List<String> snapshot(int limit) {
            if (limit <= 0 || deque.isEmpty()) {
                return Collections.emptyList();
            }
            int size = Math.min(limit, deque.size());
            List<String> copy = new ArrayList<>(size);
            int skip = deque.size() - size;
            int index = 0;
            for (String value : deque) {
                if (index++ >= skip) {
                    copy.add(value);
                }
            }
            return copy;
        }
    }
}
