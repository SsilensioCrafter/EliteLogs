package com.elitelogs.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class ConsoleHook {
    private final LogRouter router;
    private Handler handler;
    private final Formatter formatter = new SimpleFormatter();
    private LoggerContext log4jContext;
    private LoggerConfig log4jRoot;
    private Appender log4jAppender;
    private volatile boolean log4jAttached;

    public ConsoleHook(LogRouter router){ this.router = router; }

    public void hook(){
        Logger root = java.util.logging.LogManager.getLogManager().getLogger("");
        handler = new Handler() {
            @Override public void publish(LogRecord r) {
                if (!isLoggable(r)) {
                    return;
                }
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
        hookLog4j();
    }

    public void unhook(){
        if (handler != null){
            Logger root = java.util.logging.LogManager.getLogManager().getLogger("");
            root.removeHandler(handler);
            handler = null;
        }
        unhookLog4j();
    }

    public boolean isLog4jAttached() {
        return log4jAttached;
    }

    private void hookLog4j() {
        try {
            log4jContext = (LoggerContext) LogManager.getContext(false);
            if (log4jContext == null) {
                return;
            }
            Configuration configuration = log4jContext.getConfiguration();
            log4jRoot = configuration.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
            RouterAppender appender = new RouterAppender();
            appender.start();
            log4jAppender = appender;
            configuration.addAppender(appender);
            log4jRoot.addAppender(appender, org.apache.logging.log4j.Level.ALL, null);
            log4jContext.updateLoggers();
            Logger.getLogger("EliteLogs").info("[ConsoleHook] attached (Log4j2)");
            log4jAttached = true;
        } catch (Throwable t) {
            Logger.getLogger("EliteLogs").fine("[ConsoleHook] log4j attach failed: " + t.getMessage());
            log4jAttached = false;
            unhookLog4j();
        }
    }

    private void unhookLog4j() {
        if (log4jContext != null && log4jRoot != null && log4jAppender != null) {
            try {
                Configuration configuration = log4jContext.getConfiguration();
                configuration.removeAppender(log4jAppender.getName());
                log4jRoot.removeAppender(log4jAppender.getName());
                log4jContext.updateLoggers();
            } catch (Throwable ignored) {
            }
            try {
                log4jAppender.stop();
            } catch (Throwable ignored) {
            }
        }
        log4jAppender = null;
        log4jRoot = null;
        log4jContext = null;
        log4jAttached = false;
    }

    private final class RouterAppender extends AbstractAppender {
        protected RouterAppender() {
            super("EliteLogs-Log4j", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            String line = formatEvent(event);
            if (line == null || line.isEmpty()) {
                return;
            }
            org.apache.logging.log4j.Level level = event.getLevel();
            if (level.isMoreSpecificThan(org.apache.logging.log4j.Level.ERROR)) {
                router.error(line);
                router.console(line);
            } else if (level.isMoreSpecificThan(org.apache.logging.log4j.Level.WARN)) {
                router.warn(line);
                router.console(line);
            } else {
                router.console(line);
            }
        }

        private String formatEvent(LogEvent event) {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(event.getLevel()).append("]");
            String loggerName = event.getLoggerName();
            if (loggerName != null && !loggerName.isEmpty()) {
                sb.append(" [").append(loggerName).append("]");
            }
            String message = event.getMessage() != null ? event.getMessage().getFormattedMessage() : "";
            if (message != null && !message.isEmpty()) {
                sb.append(" ").append(message);
            }
            Throwable thrown = event.getThrown();
            if (thrown != null) {
                StringWriter sw = new StringWriter();
                try (PrintWriter pw = new PrintWriter(sw)) {
                    thrown.printStackTrace(pw);
                }
                sb.append(System.lineSeparator()).append(sw);
            }
            return sb.toString();
        }
    }
}
