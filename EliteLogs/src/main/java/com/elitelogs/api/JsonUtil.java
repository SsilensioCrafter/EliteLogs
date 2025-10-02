package com.elitelogs.api;

import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.Map;

final class JsonUtil {
    private JsonUtil() {
    }

    static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append("\\u");
                        String hex = Integer.toHexString(c);
                        for (int pad = hex.length(); pad < 4; pad++) {
                            sb.append('0');
                        }
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        appendJson(sb, value);
        return sb.toString();
    }

    private static void appendJson(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
            return;
        }
        if (value instanceof String) {
            sb.append(quote((String) value));
            return;
        }
        if (value instanceof Number) {
            appendNumber(sb, (Number) value);
            return;
        }
        if (value instanceof Boolean) {
            sb.append(((Boolean) value) ? "true" : "false");
            return;
        }
        if (value instanceof Map<?, ?>) {
            appendMap(sb, (Map<?, ?>) value);
            return;
        }
        if (value instanceof Iterable<?>) {
            appendIterable(sb, (Iterable<?>) value);
            return;
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            appendArray(sb, value);
            return;
        }
        sb.append(quote(String.valueOf(value)));
    }

    private static void appendNumber(StringBuilder sb, Number number) {
        if (number instanceof Double || number instanceof Float) {
            double value = number.doubleValue();
            if (Double.isFinite(value)) {
                sb.append(trimTrailingZeros(Double.toString(value)));
            } else {
                sb.append('0');
            }
            return;
        }
        sb.append(number.toString());
    }

    private static String trimTrailingZeros(String value) {
        int dotIndex = value.indexOf('.');
        if (dotIndex < 0) {
            return value;
        }
        int end = value.length();
        while (end > dotIndex && value.charAt(end - 1) == '0') {
            end--;
        }
        if (end > dotIndex && value.charAt(end - 1) == '.') {
            end--;
        }
        return value.substring(0, end);
    }

    private static void appendMap(StringBuilder sb, Map<?, ?> map) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            if (key == null) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(quote(String.valueOf(key)));
            sb.append(':');
            appendJson(sb, entry.getValue());
        }
        sb.append('}');
    }

    private static void appendIterable(StringBuilder sb, Iterable<?> iterable) {
        sb.append('[');
        Iterator<?> iterator = iterable.iterator();
        boolean first = true;
        while (iterator.hasNext()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            appendJson(sb, iterator.next());
        }
        sb.append(']');
    }

    private static void appendArray(StringBuilder sb, Object array) {
        sb.append('[');
        int length = Array.getLength(array);
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            appendJson(sb, Array.get(array, i));
        }
        sb.append(']');
    }
}
