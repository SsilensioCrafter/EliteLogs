package com.elitelogs.utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.*;

public class ConsoleHook {
    private final LogRouter router;
    private Handler handler;
    private final Formatter formatter = new SimpleFormatter();

    public ConsoleHook(LogRouter router){ this.router = router; }

    public void hook(){
        Logger root = LogManager.getLogManager().getLogger("");
        handler = new Handler() {
            @Override public void publish(LogRecord r) {
                String formatted = formatter.formatMessage(r);
                if (r.getThrown() != null) {
                    StringWriter sw = new StringWriter();
                    try (PrintWriter pw = new PrintWriter(sw)) {
                        r.getThrown().printStackTrace(pw);
                    }
                    formatted = formatted + System.lineSeparator() + sw;
                }
                String line = r.getLevel().getName() + ": " + formatted;
                boolean logToConsole = true;
                if (r.getLevel().intValue() >= Level.SEVERE.intValue()) {
                    router.error(line);
                } else if (r.getLevel().intValue() >= Level.WARNING.intValue()) {
                    router.warn(line);
                } else {
                    String up = line.toUpperCase();
                    if (up.contains("] [ERROR") || up.startsWith("ERROR") || up.contains(" ERROR ")) {
                        router.error(line);
                    } else if (up.contains("] [WARN") || up.startsWith("WARN") || up.contains(" WARN ")) {
                        router.warn(line);
                    } else {
                        router.console(line);
                        logToConsole = false;
                    }
                }
                if (logToConsole) {
                    router.console(line);
                }
            }
            @Override public void flush(){}
            @Override public void close() throws SecurityException {}
        };
        root.addHandler(handler);
        Logger.getLogger("EliteLogs").info("[ConsoleHook] attached (JUL)");
    }

    public void unhook(){
        if (handler != null){
            Logger root = LogManager.getLogManager().getLogger("");
            root.removeHandler(handler);
            handler = null;
        }
    }
}
