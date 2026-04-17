package com.arquitectura.infraestructura.logs;

import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class LogConfig {

    private static volatile boolean configured = false;

    private LogConfig() {
    }

    public static void configureRootLogger() {
        if (configured) {
            return;
        }

        synchronized (LogConfig.class) {
            if (configured) {
                return;
            }

            Logger rootLogger = Logger.getLogger("");
            for (Handler handler : rootLogger.getHandlers()) {
                rootLogger.removeHandler(handler);
            }

            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setLevel(Level.ALL);
            consoleHandler.setFormatter(new Formatter() {
                @Override
                public String format(LogRecord record) {
                    return String.format(
                            "[%1$tF %1$tT] [%2$-7s] [%3$s] %4$s%n",
                            record.getMillis(),
                            record.getLevel().getName(),
                            record.getLoggerName(),
                            formatMessage(record)
                    );
                }
            });

            rootLogger.addHandler(consoleHandler);
            rootLogger.setLevel(Level.INFO);
            configured = true;
        }
    }
}
