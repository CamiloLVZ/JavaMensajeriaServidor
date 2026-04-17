package com.arquitectura.infraestructura.logs;

import com.arquitectura.aplicacion.ContextoSolicitud;
import com.arquitectura.dominio.repositorios.JpaLogServidorRepository;
import com.arquitectura.dominio.repositorios.LogServidorRepository;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class DatabaseLogHandler extends Handler {

    private static final ThreadLocal<Boolean> EN_PUBLICACION = ThreadLocal.withInitial(() -> false);

    private final LogServidorRepository logServidorRepository = new JpaLogServidorRepository();

    public DatabaseLogHandler() {
        setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                StringBuilder builder = new StringBuilder(formatMessage(record));
                if (record.getThrown() != null) {
                    StringWriter stringWriter = new StringWriter();
                    record.getThrown().printStackTrace(new PrintWriter(stringWriter));
                    builder.append(System.lineSeparator()).append(stringWriter);
                }
                return builder.toString();
            }
        });
    }

    @Override
    public void publish(LogRecord record) {
        if (!isLoggable(record) || EN_PUBLICACION.get()) {
            return;
        }

        try {
            EN_PUBLICACION.set(true);
            logServidorRepository.guardar(
                    record.getLevel().getName(),
                    getFormatter().format(record),
                    record.getLoggerName(),
                    ContextoSolicitud.obtenerIpRemitente(),
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(record.getMillis()), ZoneId.systemDefault())
            );
        } catch (Exception e) {
            reportError("No fue posible persistir el log en base de datos", e, ErrorManager.WRITE_FAILURE);
        } finally {
            EN_PUBLICACION.remove();
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }
}
