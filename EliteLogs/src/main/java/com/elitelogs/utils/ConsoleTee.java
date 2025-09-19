package com.elitelogs.utils;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class ConsoleTee {
    private final LogRouter router;
    private PrintStream oldOut;
    private PrintStream oldErr;
    private volatile boolean preferLog4j;

    public ConsoleTee(LogRouter router){ this.router = router; }

    public void hook(){
        oldOut = System.out;
        oldErr = System.err;
        System.setOut(createTee(oldOut, false));
        System.setErr(createTee(oldErr, true));
    }
    public void unhook(){
        if (oldOut != null) System.setOut(oldOut);
        if (oldErr != null) System.setErr(oldErr);
    }

    public void setPreferLog4j(boolean prefer){
        this.preferLog4j = prefer;
    }

    private class TeeOutputStream extends OutputStream {
        private final PrintStream base;
        private final boolean isErr;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        TeeOutputStream(PrintStream base, boolean isErr){ this.base = base; this.isErr = isErr; }

        @Override public synchronized void write(int b){
            base.write(b);
            handleByte((byte) b);
        }

        @Override public synchronized void write(byte[] b, int off, int len){
            base.write(b, off, len);
            for (int i = off; i < off + len; i++) {
                handleByte(b[i]);
            }
        }

        @Override public synchronized void flush() {
            base.flush();
            flushBuffer();
        }

        @Override public synchronized void close() {
            flush();
        }

        private void handleByte(byte b) {
            if (b == '\n' || b == '\r') {
                flushBuffer();
            } else {
                buffer.write(b);
            }
        }

        private void flushBuffer(){
            if (buffer.size() == 0) {
                return;
            }
            String line = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            buffer.reset();
            if (line.isEmpty()) {
                return;
            }
            if (isErr) {
                router.error(line);
                router.console(line);
                return;
            }
            if (preferLog4j && line.contains("] [")) {
                router.console(line);
                return;
            }
            String up = line.toUpperCase(Locale.ROOT);
            if (up.contains("[ERROR") || up.startsWith("ERROR") || up.contains(" ERROR ")) {
                router.error(line);
                router.console(line);
            }
            else if (up.contains("[WARN") || up.startsWith("WARN") || up.contains(" WARN ")) {
                router.warn(line);
                router.console(line);
            }
            else {
                router.console(line);
            }
        }
    }

    private PrintStream createTee(PrintStream base, boolean isErr) {
        try {
            return new PrintStream(new TeeOutputStream(base, isErr), true, "UTF-8");
        } catch (java.io.UnsupportedEncodingException ex) {
            throw new IllegalStateException("UTF-8 not supported", ex);
        }
    }
}
