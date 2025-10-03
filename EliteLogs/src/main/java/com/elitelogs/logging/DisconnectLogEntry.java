package com.elitelogs.logging;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable payload that represents a structured disconnect event.
 *
 * <p>The listener composes the payload with contextual attributes and hands it
 * to the {@link LogRouter}. Having a dedicated type makes the API easier to
 * evolve – additional metadata can be added without changing every call site
 * and callers no longer need to manually format log lines.</p>
 */
public final class DisconnectLogEntry {
    private final UUID uuid;
    private final String playerName;
    private final String phase;
    private final Map<String, String> attributes;
    private final String rawLine;

    private DisconnectLogEntry(Builder builder) {
        this.uuid = builder.uuid;
        this.playerName = normalizeName(builder.playerName);
        this.phase = normalizePhase(builder.phase);
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(builder.attributes));
        this.rawLine = builder.rawLine;
    }

    public static Builder phase(String phase) {
        return new Builder(phase);
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getPhase() {
        return phase;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public boolean hasRawLine() {
        return rawLine != null && !rawLine.isEmpty();
    }

    public String formatMessage() {
        if (hasRawLine()) {
            return rawLine;
        }
        if (phase == null || phase.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append('[').append(phase).append(']');
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            builder.append(' ').append(entry.getKey()).append('=')
                    .append(entry.getValue());
        }
        return builder.toString();
    }

    public String resolvePlayerName() {
        return playerName != null && !playerName.isEmpty() ? playerName : "unknown";
    }

    private static String normalizeName(String input) {
        String sanitized = sanitize(input);
        if (sanitized == null || sanitized.isEmpty()) {
            return null;
        }
        return sanitized;
    }

    private static String normalizePhase(String phase) {
        if (phase == null) {
            return null;
        }
        String sanitized = sanitize(phase);
        if (sanitized == null || sanitized.isEmpty()) {
            return null;
        }
        return sanitized.toLowerCase(Locale.ROOT);
    }

    static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.replace('\n', ' ').replace("\r", " ").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static final class Builder {
        private final String phase;
        private UUID uuid;
        private String playerName;
        private final LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        private String rawLine;

        private Builder(String phase) {
            this.phase = phase;
        }

        public Builder player(UUID uuid, String playerName) {
            this.uuid = uuid;
            this.playerName = playerName;
            return this;
        }

        public Builder player(UUID uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder playerName(String playerName) {
            this.playerName = playerName;
            return this;
        }

        public Builder attribute(String key, String value) {
            if (key == null || key.isEmpty()) {
                return this;
            }
            String sanitizedKey = key.toLowerCase(Locale.ROOT).replace(' ', '-');
            String sanitizedValue = sanitize(value);
            if (sanitizedValue != null && !sanitizedValue.isEmpty()) {
                attributes.put(sanitizedKey, sanitizedValue);
            }
            return this;
        }

        public Builder attributeRaw(String key, Object value) {
            if (value == null) {
                return this;
            }
            return attribute(key, Objects.toString(value));
        }

        public Builder rawMessage(String message) {
            this.rawLine = sanitize(message);
            return this;
        }

        public DisconnectLogEntry build() {
            if (rawLine != null && rawLine.isEmpty()) {
                rawLine = null;
            }
            return new DisconnectLogEntry(this);
        }
    }
}
