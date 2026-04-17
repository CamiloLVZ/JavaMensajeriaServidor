package com.arquitectura.ejemplosClientes.tcp;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * Cliente TCP SIMPLIFICADO para envío de archivos grandes.
 * Usa JSON con streaming de datos para evitar cargar todo en memoria.
 */
public class ClienteArchivoTCPStreaming {

    private static final Logger LOGGER = Logger.getLogger(ClienteArchivoTCPStreaming.class.getName());
    private static final int TAMAÑO_CHUNK = 512 * 1024; // 512 KB por chunk

    private String host;
    private int puerto;

    public ClienteArchivoTCPStreaming(String host, int puerto) {
        this.host = host;
        this.puerto = puerto;
    }

    /**
     * Envía un archivo por TCP en chunks sin cargar todo en memoria
     */
    public void enviarArchivoGrande(Path rutaArchivo) throws Exception {
        if (!Files.exists(rutaArchivo)) {
            throw new FileNotFoundException("Archivo no encontrado: " + rutaArchivo);
        }

        long tamanoArchivo = Files.size(rutaArchivo);
        long totalChunks = (tamanoArchivo + TAMAÑO_CHUNK - 1) / TAMAÑO_CHUNK;

        String transferId = UUID.randomUUID().toString();
        String nombreArchivo = rutaArchivo.getFileName().toString();

        System.out.println("\n╔═══════════════════════════════════════════════════════╗");
        System.out.println("║   TRANSFERENCIA STREAMING TCP (SIN CARGAR EN RAM)   ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝");
        System.out.println("Archivo: " + nombreArchivo);
        System.out.println("Tamaño: " + (tamanoArchivo / (1024*1024)) + " MB");
        System.out.println("Chunks: " + totalChunks + " x " + (TAMAÑO_CHUNK / 1024) + "KB");
        System.out.println("ID Transferencia: " + transferId);
        System.out.println("");

        try (FileInputStream fis = new FileInputStream(rutaArchivo.toFile())) {
            byte[] buffer = new byte[TAMAÑO_CHUNK];
            long indexChunk = 0;
            int bytesLeidos;
            long totalEnviado = 0;

            while ((bytesLeidos = fis.read(buffer)) != -1) {
                // Convertir a Base64 para enviar como JSON
                byte[] chunkData = new byte[bytesLeidos];
                System.arraycopy(buffer, 0, chunkData, 0, bytesLeidos);
                String chunkBase64 = Base64.getEncoder().encodeToString(chunkData);

                // Crear JSON simple
                String json = "{\"tipo\":\"CHUNK\",\"id\":\"" + transferId + "\",\"chunk\":" + indexChunk +
                             ",\"total\":" + totalChunks + ",\"datos\":\"" + chunkBase64 + "\"}";

                // Enviar al servidor
                enviarJSON(json);

                totalEnviado += bytesLeidos;
                indexChunk++;

                // Mostrar progreso
                long porcentaje = (totalEnviado * 100) / tamanoArchivo;
                int barras = (int)(porcentaje / 5);
                System.out.print("\r[" + "=".repeat(Math.max(0, barras)) +
                                 " ".repeat(Math.max(0, 20 - barras)) + "] " + porcentaje + "%");

                Thread.sleep(10); // Pequeña pausa para no saturar
            }

            System.out.println("\n\n✅ Transferencia completada exitosamente!");
            System.out.println("Total de chunks enviados: " + indexChunk);
            System.out.println("Total de bytes: " + totalEnviado);

        } catch (IOException e) {
            System.err.println("\n❌ Error: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Envía un JSON al servidor
     */
    private void enviarJSON(String json) throws Exception {
        try (Socket socket = new Socket(host, puerto);
             PrintWriter writer = new PrintWriter(
                 new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
             BufferedReader reader = new BufferedReader(
                 new InputStreamReader(socket.getInputStream(), "UTF-8"))) {

            // Enviar JSON
            writer.println(json);
            writer.flush();

            // Esperar respuesta del servidor
            String respuesta = reader.readLine();
            if (respuesta != null && respuesta.contains("OK")) {
                LOGGER.fine("Chunk enviado correctamente");
            }

        } catch (IOException e) {
            LOGGER.warning("Error enviando chunk: " + e.getMessage());
            throw e;
        }
    }

    public static void main(String[] args) throws Exception {
        String host = "localhost";
        int puerto = 8080;

        ClienteArchivoTCPStreaming cliente = new ClienteArchivoTCPStreaming(host, puerto);

        // Crear archivo de prueba
        Path archivoTest = Path.of("test-100mb.bin");
        if (!Files.exists(archivoTest)) {
            System.out.println("Creando archivo de prueba de 100 MB...");
            crearArchivoTest(archivoTest, 100 * 1024 * 1024L);
            System.out.println("✓ Archivo creado: " + archivoTest.toAbsolutePath() + "\n");
        }

        long inicio = System.currentTimeMillis();
        try {
            cliente.enviarArchivoGrande(archivoTest);
            long duracion = (System.currentTimeMillis() - inicio) / 1000;
            System.out.println("Tiempo total: " + duracion + " segundos\n");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void crearArchivoTest(Path ruta, long tamaño) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(ruta.toFile())) {
            byte[] buffer = new byte[1024 * 1024]; // 1 MB
            long bytesEscritos = 0;

            while (bytesEscritos < tamaño) {
                int bytesAEscribir = (int) Math.min(buffer.length, tamaño - bytesEscritos);
                fos.write(buffer, 0, bytesAEscribir);
                bytesEscritos += bytesAEscribir;

                if (bytesEscritos % (10 * 1024 * 1024) == 0) {
                    System.out.println("  Creado: " + (bytesEscritos / (1024*1024)) + " MB / " +
                                     (tamaño / (1024*1024)) + " MB");
                }
            }
        }
    }
}

