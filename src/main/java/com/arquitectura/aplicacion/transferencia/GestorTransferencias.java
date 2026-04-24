package com.arquitectura.aplicacion.transferencia;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Registro en memoria de las transferencias de archivos en curso.
 *
 * Cada transferencia tiene un {@link EstadoTransferencia} que contiene:
 * - metadatos del archivo (nombre, extensión, tamaño esperado)
 * - path del archivo temporal en disco (donde se escriben los chunks)
 * - SHA-256 incremental calculado chunk a chunk
 * - cantidad de chunks recibidos
 *
 * Thread-safe: usa ConcurrentHashMap para acceso concurrente.
 */
public class GestorTransferencias {

    private static final Logger LOGGER = Logger.getLogger(GestorTransferencias.class.getName());

    private static final GestorTransferencias INSTANCE = new GestorTransferencias();

    private final ConcurrentHashMap<String, EstadoTransferencia> transferencias = new ConcurrentHashMap<>();

    private GestorTransferencias() {}

    public static GestorTransferencias getInstance() {
        return INSTANCE;
    }

    /**
     * Registra una nueva transferencia entrante.
     *
     * @param transferId    UUID único de la transferencia
     * @param nombreArchivo nombre original del archivo
     * @param extension     extensión sin punto
     * @param tamanoTotal   bytes totales esperados
     * @param totalChunks   cantidad de chunks esperados
     * @param rutaTemporal  path donde se irán escribiendo los chunks
     */
    public void registrar(String transferId, String nombreArchivo, String extension,
                          long tamanoTotal, long totalChunks, Path rutaTemporal) {
        EstadoTransferencia estado = new EstadoTransferencia(
                transferId, nombreArchivo, extension, tamanoTotal, totalChunks, rutaTemporal);
        transferencias.put(transferId, estado);
        LOGGER.info(() -> "Transferencia registrada: " + transferId + " | archivo: " + nombreArchivo);
    }

    /**
     * Devuelve el estado de una transferencia activa, o null si no existe.
     */
    public EstadoTransferencia obtener(String transferId) {
        return transferencias.get(transferId);
    }

    /**
     * Elimina la transferencia del registro (al completarse o cancelarse).
     */
    public void eliminar(String transferId) {
        transferencias.remove(transferId);
    }

    /**
     * Verifica si existe una transferencia activa con ese id.
     */
    public boolean existe(String transferId) {
        return transferencias.containsKey(transferId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Estado interno de una transferencia
    // ─────────────────────────────────────────────────────────────────────────

    public static class EstadoTransferencia {

        private final String transferId;
        private final String nombreArchivo;
        private final String extension;
        private final long tamanoTotal;
        private final long totalChunks;
        private final Path rutaTemporal;
        private final Instant inicio;

        /** SHA-256 incremental — se actualiza con cada chunk recibido. */
        private final MessageDigest digest;

        private long chunksRecibidos = 0;
        private long bytesRecibidos = 0;

        EstadoTransferencia(String transferId, String nombreArchivo, String extension,
                            long tamanoTotal, long totalChunks, Path rutaTemporal) {
            this.transferId = transferId;
            this.nombreArchivo = nombreArchivo;
            this.extension = extension;
            this.tamanoTotal = tamanoTotal;
            this.totalChunks = totalChunks;
            this.rutaTemporal = rutaTemporal;
            this.inicio = Instant.now();
            try {
                this.digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 no disponible", e);
            }
        }

        /**
         * Actualiza el digest incremental con los bytes de un chunk.
         * Debe llamarse en el mismo orden en que llegan los chunks.
         */
        public synchronized void actualizarDigest(byte[] chunk) {
            digest.update(chunk);
        }

        /**
         * Devuelve el hash SHA-256 calculado hasta el momento, en Base64.
         * NO finaliza el digest — se puede seguir acumulando.
         */
        public synchronized String hashActualBase64() {
            try {
                MessageDigest copia = (MessageDigest) digest.clone();
                return Base64.getEncoder().encodeToString(copia.digest());
            } catch (CloneNotSupportedException e) {
                throw new IllegalStateException("No se pudo clonar el digest", e);
            }
        }

        /**
         * Finaliza el digest y devuelve el hash SHA-256 completo en Base64.
         * Solo debe llamarse una vez al finalizar la transferencia.
         */
        public synchronized String hashFinalBase64() {
            return Base64.getEncoder().encodeToString(digest.digest());
        }

        public synchronized void registrarChunk(int bytesChunk) {
            chunksRecibidos++;
            bytesRecibidos += bytesChunk;
        }

        public boolean estaCompleta() {
            return chunksRecibidos >= totalChunks;
        }

        public void eliminarArchivoParcial() {
            try {
                Files.deleteIfExists(rutaTemporal);
            } catch (IOException e) {
                LOGGER.warning(() -> "No se pudo eliminar archivo parcial: " + rutaTemporal);
            }
        }

        public String getTransferId() { return transferId; }
        public String getNombreArchivo() { return nombreArchivo; }
        public String getExtension() { return extension; }
        public long getTamanoTotal() { return tamanoTotal; }
        public long getTotalChunks() { return totalChunks; }
        public Path getRutaTemporal() { return rutaTemporal; }
        public Instant getInicio() { return inicio; }
        public long getChunksRecibidos() { return chunksRecibidos; }
        public long getBytesRecibidos() { return bytesRecibidos; }
    }
}
