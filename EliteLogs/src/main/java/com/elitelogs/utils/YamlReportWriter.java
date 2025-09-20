package com.elitelogs.utils;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Small helper to emit predictable, escaped YAML tailored for the plugin's
 * human-readable reports. It keeps indentation consistent and ensures all
 * scalar values are safely quoted when necessary.
 */
public final class YamlReportWriter {
    private final PrintWriter out;
    private int indent;

    public YamlReportWriter(PrintWriter out) {
        this.out = Objects.requireNonNull(out, "out");
    }

    public YamlReportWriter(Writer writer) {
        this(writer instanceof PrintWriter ? (PrintWriter) writer : new PrintWriter(writer));
    }

    public void blankLine() {
        out.println();
    }

    public void scalar(String key, Object value) {
        writeIndent();
        out.print(key);
        out.print(": ");
        out.println(formatScalar(value));
    }

    public void emptyList(String key) {
        writeIndent();
        out.print(key);
        out.println(": []");
    }

    public void section(String key, Consumer<YamlReportWriter> body) {
        if (body == null) {
            return;
        }
        writeIndent();
        out.print(key);
        out.println(':');
        indent += 2;
        body.accept(this);
        indent = Math.max(0, indent - 2);
    }

    public void list(String key, Consumer<ListWriter> body) {
        if (body == null) {
            return;
        }
        writeIndent();
        out.print(key);
        out.println(':');
        indent += 2;
        body.accept(new ListWriter());
        indent = Math.max(0, indent - 2);
    }

    public void flush() {
        out.flush();
    }

    private void writeIndent() {
        for (int i = 0; i < indent; i++) {
            out.print(' ');
        }
    }

    private String formatScalar(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return quote(String.valueOf(value));
    }

    public static String quote(String value) {
        if (value == null) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder(value.length() + 2);
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
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    public final class ListWriter {
        private ListWriter() {
        }

        public void item(Consumer<YamlReportWriter> body) {
            if (body == null) {
                return;
            }
            writeIndent();
            out.println("- ");
            indent += 2;
            body.accept(YamlReportWriter.this);
            indent = Math.max(0, indent - 2);
        }

        public void itemScalar(Object value) {
            writeIndent();
            out.print("- ");
            out.println(formatScalar(value));
        }
    }
}
