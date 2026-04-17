package com.arquitectura.infraestructura.transporte;

import com.arquitectura.comun.dto.PaqueteDatos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class TcpProtocoloTransporte implements ProtocoloTransporte {

    private static final Logger LOGGER = Logger.getLogger(TcpProtocoloTransporte.class.getName());
    private static final Path DIRECTORIO_DESTINO = Path.of("archivos-recibidos");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Mapa para rastrear transferencias en progreso: transferId -> info de transferencia
    private final Map<String, TransferenciaInfo> transferenciasActivas = new HashMap<>();

    private ServerSocket serverSocket;
    private boolean activo = false;
    private GestorTransferenciasStreaming gestorTransferencias;

    @Override
    public void iniciar(int puerto) {
        try {
            serverSocket = new ServerSocket(puerto);
            activo = true;
            gestorTransferencias = new GestorTransferenciasStreaming();

            // Crear directorio de destino si no existe
            Files.createDirectories(DIRECTORIO_DESTINO);

            LOGGER.info(() -> "Transporte TCP iniciado en puerto " + puerto);
            LOGGER.info(() -> "Directorio de destino: " + DIRECTORIO_DESTINO.toAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException("Error iniciando TCP", e);
        }
    }

    @Override
    public void enviar(byte[] datos, String hostDestino, int puertoDestino) {
        try (Socket socket = new Socket(hostDestino, puertoDestino);
             OutputStream output = socket.getOutputStream()) {

            LOGGER.info(() -> "Enviando " + datos.length + " bytes por TCP a " + hostDestino + ":" + puertoDestino);
            output.write(datos);
            output.flush();

        } catch (Exception e) {
            throw new RuntimeException("Error enviando datos por TCP", e);
        }
    }

    @Override
    public PaqueteDatos recibir() {
        try {
            Socket cliente = serverSocket.accept();
            LOGGER.fine(() -> "Nueva conexión recibida de: " + cliente.getInetAddress().getHostAddress());

            // Leer JSON directamente usando BufferedReader
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(cliente.getInputStream(), StandardCharsets.UTF_8));
            String json = reader.readLine();

            if (json == null || json.isEmpty()) {
                LOGGER.warning("Línea JSON vacía recibida");
                // Enviar respuesta de error
                try (PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(cliente.getOutputStream(), StandardCharsets.UTF_8))) {
                    writer.println("{\"estado\":\"error\",\"mensaje\":\"línea vacía\"}");
                    writer.flush();
                }
                return new PaqueteDatos(new byte[0], cliente);
            }

            LOGGER.fine(() -> "Mensaje JSON recibido, longitud: " + json.length());

            // Procesar el JSON para detectar si es una transferencia de chunk
            boolean esChunkStreaming = procesarTransferencia(json);

            // Enviar respuesta OK al cliente
            try (PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(cliente.getOutputStream(), StandardCharsets.UTF_8))) {
                writer.println("{\"estado\":\"OK\"}");
                writer.flush();
            }

            if (esChunkStreaming) {
                // El chunk ya fue procesado por este transporte, no debe pasar al router de negocio
                return new PaqueteDatos(new byte[0], cliente);
            }

            return new PaqueteDatos(json.getBytes(StandardCharsets.UTF_8), cliente);

        } catch (Exception e) {
            LOGGER.severe(() -> "Error recibiendo datos por TCP: " + e.getMessage());
            throw new RuntimeException("Error recibiendo datos por TCP", e);
        }
    }

    /**
     * Procesa un mensaje JSON de transferencia de chunk
     */
    private boolean procesarTransferencia(String json) {
        try {
            JsonNode node = MAPPER.readTree(json);

            // Verificar si es un mensaje de transferencia de chunk
            if (!node.has("tipo")) {
                // No es un mensaje de transferencia, ignorar silenciosamente
                return false;
            }

            String tipo = node.get("tipo").asText();
            if (!tipo.equals("CHUNK")) {
                // No es un chunk, ignorar silenciosamente
                return false;
            }

            // Validar que tenga todos los campos requeridos
            if (!node.has("id") || !node.has("chunk") || !node.has("total") || !node.has("datos")) {
                LOGGER.warning("Mensaje CHUNK incompleto, faltan campos");
                return true;
            }

            String transferId = node.get("id").asText();
            long chunkNum = node.get("chunk").asLong();
            long totalChunks = node.get("total").asLong();
            String datosBase64 = node.get("datos").asText();

            // Obtener o crear info de transferencia
            TransferenciaInfo info = transferenciasActivas.computeIfAbsent(transferId, k ->
                new TransferenciaInfo(transferId, totalChunks));

            // Decodificar datos del chunk
            byte[] chunkDatos = Base64.getDecoder().decode(datosBase64);

            // Escribir chunk al archivo temporal
            Path rutaTemporal = DIRECTORIO_DESTINO.resolve(transferId + ".tmp");
            Files.write(rutaTemporal, chunkDatos, StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            info.chunksRecibidos++;
            info.bytesRecibidos += chunkDatos.length;

            LOGGER.fine(() -> "Chunk recibido: transferencia=" + transferId +
                              " chunk=" + (chunkNum + 1) + "/" + totalChunks);

            // Si hemos recibido todos los chunks, finalizar la transferencia
            if (info.chunksRecibidos >= totalChunks) {
                finalizarTransferencia(transferId, info);
            }

            return true;

        } catch (IllegalArgumentException e) {
            // Error decodificando Base64
            LOGGER.warning(() -> "Error decodificando Base64 en chunk: " + e.getMessage());
            return true;
        } catch (Exception e) {
            LOGGER.warning(() -> "Error procesando transferencia: " + e.getMessage());
            return false;
        }
    }

    /**
     * Finaliza una transferencia y mueve el archivo temporal a su ubicación final
     */
    private void finalizarTransferencia(String transferId, TransferenciaInfo info) {
        try {
            Path rutaTemporal = DIRECTORIO_DESTINO.resolve(transferId + ".tmp");
            Path rutaFinal = DIRECTORIO_DESTINO.resolve("archivo_" + System.currentTimeMillis() + ".bin");

            // Mover archivo temporal a ubicación final
            if (Files.exists(rutaTemporal)) {
                Files.move(rutaTemporal, rutaFinal);

                long tamanoFinal = Files.size(rutaFinal);
                long tamanoMB = tamanoFinal / (1024*1024);

                LOGGER.info(() -> "═══════════════════════════════════════════════════════");
                LOGGER.info(() -> "✓ TRANSFERENCIA COMPLETADA EXITOSAMENTE");
                LOGGER.info(() -> "  Archivo: " + rutaFinal.getFileName());
                LOGGER.info(() -> "  Tamaño: " + tamanoMB + " MB");
                LOGGER.info(() -> "  Ubicación: " + rutaFinal.toAbsolutePath());
                LOGGER.info(() -> "═══════════════════════════════════════════════════════");

                System.out.println("[SERVIDOR] ✓ Archivo guardado en: " + rutaFinal.toAbsolutePath());
            }

            // Limpiar
            transferenciasActivas.remove(transferId);

        } catch (Exception e) {
            LOGGER.severe(() -> "Error finalizando transferencia: " + e.getMessage());
        }
    }

    /**
     * Obtiene el gestor de transferencias para acceso directo.
     */
    public GestorTransferenciasStreaming getGestorTransferencias() {
        return gestorTransferencias;
    }

    @Override
    public void detener() {
        activo = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                LOGGER.info("Transporte TCP detenido");
            }
        } catch (Exception e) {
            throw new RuntimeException("Error cerrando TCP", e);
        }
    }

    @Override
    public String getNombre() {
        return "TCP";
    }

    /**
     * Clase interna para rastrear el estado de una transferencia
     */
    private static class TransferenciaInfo {
        String transferId;
        long totalChunks;
        long chunksRecibidos;
        long bytesRecibidos;

        TransferenciaInfo(String transferId, long totalChunks) {
            this.transferId = transferId;
            this.totalChunks = totalChunks;
            this.chunksRecibidos = 0;
            this.bytesRecibidos = 0;
        }
    }
}
