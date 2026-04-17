package com.arquitectura.infraestructura.transporte;

import com.arquitectura.comun.dto.FrameTransferencia;
import com.arquitectura.dominio.modelo.TransferenciaStreamingModel;
import com.arquitectura.dominio.repositorios.JpaTransferenciaStreamingRepository;
import com.arquitectura.dominio.repositorios.TransferenciaStreamingRepository;
import com.arquitectura.infraestructura.seguridad.CryptoUtil;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;

/**
 * Gestor central de transferencias streaming de archivos grandes.
 * Maneja la recepción de chunks, validación incremental de hash,
 * reanudación de transferencias y persistencia de estado.
 */
public class GestorTransferenciasStreaming {

    private static final Logger LOGGER = Logger.getLogger(GestorTransferenciasStreaming.class.getName());
    private static final Path DIRECTORIO_TEMPORAL = Path.of("transferencias-temporales");
    private static final long TIMEOUT_TRANSFERENCIA_MS = 3600000; // 1 hora
    private static final int MAX_TRANSFERENCIAS_CONCURRENTES = 100;

    private final TransferenciaStreamingRepository repository = new JpaTransferenciaStreamingRepository();

    // Cache en memoria de transferencias activas para mejor rendimiento
    private final Map<String, TransferenciaStreamingModel> transferenciasActivas = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, MessageDigest> digestActivos = Collections.synchronizedMap(new HashMap<>());

    public GestorTransferenciasStreaming() {
        try {
            Files.createDirectories(DIRECTORIO_TEMPORAL);
            LOGGER.info("Directorio de transferencias temporales creado: " + DIRECTORIO_TEMPORAL.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.warning("No se pudo crear directorio temporal: " + e.getMessage());
        }
    }

    /**
     * Inicia una nueva transferencia streaming.
     * Retorna el transferId para ser usado en los chunks.
     */
    public String iniciarTransferencia(String nombreArchivo, long tamanoTotal, long totalChunks,
                                       String remitente, String ipRemitente, String protocolo) {
        if (transferenciasActivas.size() >= MAX_TRANSFERENCIAS_CONCURRENTES) {
            throw new RuntimeException("Máximo de transferencias concurrentes alcanzado");
        }

        String transferId = UUID.randomUUID().toString();
        Path rutaTemporal = DIRECTORIO_TEMPORAL.resolve(transferId + ".tmp");

        TransferenciaStreamingModel transferencia = new TransferenciaStreamingModel(
            transferId,
            nombreArchivo,
            tamanoTotal,
            remitente,
            ipRemitente,
            totalChunks,
            protocolo
        );
        transferencia.setRutaTemporal(rutaTemporal.toAbsolutePath().toString());

        try {
            // Crear archivo temporal
            Files.createFile(rutaTemporal);

            // Guardar en DB
            repository.guardar(transferencia);

            // Guardar en cache
            transferenciasActivas.put(transferId, transferencia);

            // Inicializar digest SHA-256
            digestActivos.put(transferId, MessageDigest.getInstance("SHA-256"));

            LOGGER.info(() -> "Transferencia iniciada: " + transferId + " | Archivo: " + nombreArchivo +
                              " | Tamaño: " + (tamanoTotal / (1024*1024)) + " MB | Total chunks: " + totalChunks);

            return transferId;
        } catch (Exception e) {
            LOGGER.severe(() -> "Error iniciando transferencia: " + e.getMessage());
            throw new RuntimeException("Error iniciando transferencia streaming", e);
        }
    }

    /**
     * Procesa un chunk recibido de una transferencia.
     */
    public synchronized boolean procesarChunk(FrameTransferencia frame) {
        String transferId = frame.getTransferId();

        TransferenciaStreamingModel transferencia = transferenciasActivas.computeIfAbsent(
            transferId,
            k -> repository.obtenerPorId(transferId).orElse(null)
        );

        if (transferencia == null) {
            LOGGER.warning(() -> "Transferencia no encontrada: " + transferId);
            return false;
        }

        // Validar que no sea una transferencia antigua (timeout)
        if (esTransferenciaExpirada(transferencia)) {
            LOGGER.warning(() -> "Transferencia expirada: " + transferId);
            cancelarTransferencia(transferId);
            return false;
        }

        try {
            // Validar índice del chunk
            if (frame.getIndexChunk() != transferencia.getChunksRecibidos()) {
                LOGGER.warning(() -> "Chunk fuera de orden. Esperado: " + transferencia.getChunksRecibidos() +
                                     ", Recibido: " + frame.getIndexChunk());
                // Para UDP: podría haber pérdida, pero TCP debe ser ordenado
                if ("TCP".equals(transferencia.getProtocolo())) {
                    return false;
                }
            }

            // Escribir chunk al archivo temporal
            byte[] chunkData = frame.getChunkData();
            if (chunkData != null && chunkData.length > 0) {
                Path rutaTemporal = Path.of(transferencia.getRutaTemporal());
                Files.write(rutaTemporal, chunkData,
                           StandardOpenOption.APPEND,
                           StandardOpenOption.CREATE);

                // Actualizar hash incremental
                MessageDigest digest = digestActivos.get(transferId);
                if (digest != null) {
                    digest.update(chunkData);
                }

                // Actualizar estado
                transferencia.setChunksRecibidos(transferencia.getChunksRecibidos() + 1);
                transferencia.setOffsetActual(transferencia.getOffsetActual() + chunkData.length);
                transferencia.setFechaUltimaActividad(LocalDateTime.now());

                // Guardar hash incremental en BD para recuperación
                if (digest != null) {
                    transferencia.setHashIncremental(((MessageDigest) digest.clone()).digest());
                }
            }

            // Verificar si transferencia está completa
            if (transferencia.getChunksRecibidos() >= transferencia.getTotalChunks()) {
                finalizarTransferencia(transferencia);
                return true;
            }

            // Actualizar en BD
            repository.actualizar(transferencia);

            if (transferencia.getChunksRecibidos() % 100 == 0) {
                long porcentaje = (transferencia.getChunksRecibidos() * 100) / transferencia.getTotalChunks();
                LOGGER.info(() -> "Progreso transferencia " + transferId + ": " + porcentaje + "% " +
                                  "(" + transferencia.getChunksRecibidos() + "/" + transferencia.getTotalChunks() + " chunks)");
            }

            return true;

        } catch (Exception e) {
            LOGGER.severe(() -> "Error procesando chunk " + frame.getIndexChunk() + " de transferencia " + transferId + ": " + e.getMessage());
            transferencia.setEstado("FAILED");
            repository.actualizar(transferencia);
            return false;
        }
    }

    /**
     * Finaliza una transferencia y mueve el archivo a la ubicación final.
     */
    private void finalizarTransferencia(TransferenciaStreamingModel transferencia) {
        try {
            MessageDigest digest = digestActivos.get(transferencia.getTransferId());
            if (digest != null) {
                byte[] hashBytes = ((MessageDigest) digest.clone()).digest();
                String hashFinal = Base64.getEncoder().encodeToString(hashBytes);
                transferencia.setHashFinal(hashFinal);
            }

            transferencia.setEstado("COMPLETED");
            transferencia.setFechaFinalizacion(LocalDateTime.now());

            // Mover archivo temporal a ubicación final
            Path rutaTemporal = Path.of(transferencia.getRutaTemporal());
            Path rutaFinal = Path.of("archivos-recibidos").resolve(construirNombreFinal(transferencia));

            Files.createDirectories(rutaFinal.getParent());
            Files.move(rutaTemporal, rutaFinal);
            transferencia.setRutaTemporal(rutaFinal.toAbsolutePath().toString());

            // Actualizar en BD
            repository.actualizar(transferencia);

            // Limpiar cache
            transferenciasActivas.remove(transferencia.getTransferId());
            digestActivos.remove(transferencia.getTransferId());

            LOGGER.info(() -> "Transferencia finalizada: " + transferencia.getTransferId() +
                              " | Archivo: " + rutaFinal.toAbsolutePath() +
                              " | Hash: " + transferencia.getHashFinal());

        } catch (Exception e) {
            LOGGER.severe(() -> "Error finalizando transferencia " + transferencia.getTransferId() + ": " + e.getMessage());
            transferencia.setEstado("FAILED");
            repository.actualizar(transferencia);
        }
    }

    /**
     * Cancela una transferencia en progreso.
     */
    public void cancelarTransferencia(String transferId) {
        try {
            Optional<TransferenciaStreamingModel> opt = repository.obtenerPorId(transferId);
            if (opt.isPresent()) {
                TransferenciaStreamingModel transferencia = opt.get();
                transferencia.setEstado("CANCELLED");
                transferencia.setFechaFinalizacion(LocalDateTime.now());
                repository.actualizar(transferencia);

                // Limpiar archivo temporal
                Path rutaTemporal = Path.of(transferencia.getRutaTemporal());
                if (Files.exists(rutaTemporal)) {
                    Files.delete(rutaTemporal);
                }

                // Limpiar cache
                transferenciasActivas.remove(transferId);
                digestActivos.remove(transferId);

                LOGGER.info(() -> "Transferencia cancelada: " + transferId);
            }
        } catch (Exception e) {
            LOGGER.severe(() -> "Error cancelando transferencia " + transferId + ": " + e.getMessage());
        }
    }

    /**
     * Obtiene el estado actual de una transferencia.
     */
    public Optional<TransferenciaStreamingModel> obtenerEstado(String transferId) {
        TransferenciaStreamingModel transferencia = transferenciasActivas.getOrDefault(
            transferId,
            repository.obtenerPorId(transferId).orElse(null)
        );
        return Optional.ofNullable(transferencia);
    }

    /**
     * Limpia transferencias expiradas.
     */
    public void limpiarTransferenciasExpiradas() {
        List<String> paraEliminar = new ArrayList<>();
        for (TransferenciaStreamingModel transferencia : transferenciasActivas.values()) {
            if (esTransferenciaExpirada(transferencia)) {
                paraEliminar.add(transferencia.getTransferId());
            }
        }
        for (String transferId : paraEliminar) {
            cancelarTransferencia(transferId);
        }
    }

    private boolean esTransferenciaExpirada(TransferenciaStreamingModel transferencia) {
        long tiempoTranscurrido = System.currentTimeMillis() -
                                 transferencia.getFechaUltimaActividad().atZone(java.time.ZoneId.systemDefault())
                                 .toInstant().toEpochMilli();
        return tiempoTranscurrido > TIMEOUT_TRANSFERENCIA_MS;
    }

    private String construirNombreFinal(TransferenciaStreamingModel transferencia) {
        String nombre = transferencia.getNombreArchivo();
        int ultimoPunto = nombre.lastIndexOf('.');

        if (ultimoPunto > 0) {
            String base = nombre.substring(0, ultimoPunto);
            String extension = nombre.substring(ultimoPunto + 1);

            Path rutaFinal = Path.of("archivos-recibidos").resolve(nombre);
            int contador = 1;
            while (Files.exists(rutaFinal)) {
                rutaFinal = Path.of("archivos-recibidos").resolve(
                    base + " (" + contador + ")." + extension
                );
                contador++;
            }
            return rutaFinal.getFileName().toString();
        }
        return nombre;
    }

    /**
     * Obtiene información del estado de todas las transferencias activas.
     */
    public Map<String, Object> obtenerEstadisticas() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("transferenciasActivas", transferenciasActivas.size());
        stats.put("maxConcurrentes", MAX_TRANSFERENCIAS_CONCURRENTES);
        stats.put("directorioTemporal", DIRECTORIO_TEMPORAL.toAbsolutePath().toString());

        long bytesEnTransferencia = transferenciasActivas.values().stream()
            .mapToLong(TransferenciaStreamingModel::getOffsetActual)
            .sum();
        stats.put("bytesEnTransferencia", bytesEnTransferencia);
        stats.put("bytesEnTransferenciaMB", bytesEnTransferencia / (1024*1024));

        return stats;
    }
}

