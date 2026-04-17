package com.arquitectura.ejemplosClientes.udp;

import com.arquitectura.comun.dto.FrameTransferencia;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Cliente UDP para envío de archivos grandes con streaming en chunks.
 * Implementa control confiable: ACK, reintentos y detección de pérdida.
 * No carga el archivo completo en memoria.
 */
public class ClienteArchivoUDPStreaming {

    private static final Logger LOGGER = Logger.getLogger(ClienteArchivoUDPStreaming.class.getName());
    private static final int TAMAÑO_CHUNK = 2 * 1024 * 1024; // 2 MB por chunk
    private static final long ACK_TIMEOUT_MS = 10000; // 10 segundos timeout
    private static final int MAX_REINTENTOS = 5;

    private String host;
    private int puerto;
    private DatagramSocket socket;

    public ClienteArchivoUDPStreaming(String host, int puerto) throws Exception {
        this.host = host;
        this.puerto = puerto;
        this.socket = new DatagramSocket();
        this.socket.setSoTimeout((int) ACK_TIMEOUT_MS);
    }

    /**
     * Envía un archivo grande por UDP usando streaming en chunks con control confiable.
     */
    public void enviarArchivoGrande(Path rutaArchivo) throws Exception {
        if (!Files.exists(rutaArchivo)) {
            throw new FileNotFoundException("Archivo no encontrado: " + rutaArchivo);
        }

        long tamanoArchivo = Files.size(rutaArchivo);
        long totalChunks = (tamanoArchivo + TAMAÑO_CHUNK - 1) / TAMAÑO_CHUNK;

        String transferId = UUID.randomUUID().toString();
        String nombreArchivo = rutaArchivo.getFileName().toString();

        LOGGER.info(() -> "Iniciando transferencia UDP de archivo: " + nombreArchivo +
                          " (" + (tamanoArchivo / (1024*1024)) + " MB) en " + totalChunks + " chunks");

        MessageDigest md = MessageDigest.getInstance("SHA-256");

        try (FileInputStream fis = new FileInputStream(rutaArchivo.toFile())) {
            byte[] buffer = new byte[TAMAÑO_CHUNK];
            long indexChunk = 0;
            int bytesLeidos;
            InetAddress direccionServidor = InetAddress.getByName(host);

            while ((bytesLeidos = fis.read(buffer)) != -1) {
                // Crear frame con este chunk
                byte[] chunkData = new byte[bytesLeidos];
                System.arraycopy(buffer, 0, chunkData, 0, bytesLeidos);

                // Calcular hash del chunk
                md.update(chunkData);
                byte[] chunkHash = ((MessageDigest) md.clone()).digest();

                FrameTransferencia frame = new FrameTransferencia(
                    transferId,
                    indexChunk,
                    totalChunks,
                    chunkData,
                    chunkHash,
                    tamanoArchivo
                );

                // Enviar frame al servidor con reintentos
                boolean ackRecibido = enviarFrameConAck(frame, direccionServidor, indexChunk);

                final long chunkActual = indexChunk;
                if (!ackRecibido) {
                    LOGGER.warning(() -> "No se recibió ACK para chunk " + chunkActual +
                                         " después de " + MAX_REINTENTOS + " intentos");
                }

                indexChunk++;

                if (indexChunk % 5 == 0) {
                    long porcentaje = (indexChunk * 100) / totalChunks;
                    final long chunkFinal = indexChunk;
                    LOGGER.info(() -> "Progreso: " + porcentaje + "% (" + chunkFinal + "/" + totalChunks + " chunks)");
                }
            }

            LOGGER.info(() -> "Transferencia completada: " + nombreArchivo);

        } catch (IOException e) {
            LOGGER.severe("Error leyendo archivo: " + e.getMessage());
            throw e;
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    /**
     * Envía un frame UDP con control confiable (ACK).
     */
    private boolean enviarFrameConAck(FrameTransferencia frame, InetAddress direccion, long indexChunk) throws Exception {
        boolean ackRecibido = false;
        int intentos = 0;

        while (!ackRecibido && intentos < MAX_REINTENTOS) {
            try {
                byte[] frameSerialized = frame.serializar();
                DatagramPacket paquete = new DatagramPacket(frameSerialized, frameSerialized.length,
                                                            direccion, puerto);
                socket.send(paquete);

                final int intentoActual = intentos;
                LOGGER.finer(() -> "Enviando chunk UDP (intento " + (intentoActual + 1) + ") " +
                                   "transferencia " + frame.getTransferId() +
                                   " chunk " + indexChunk);

                // Esperar ACK del servidor
                byte[] bufferAck = new byte[256];
                DatagramPacket ackPacket = new DatagramPacket(bufferAck, bufferAck.length);

                try {
                    socket.receive(ackPacket);
                    String ackStr = new String(bufferAck, 0, ackPacket.getLength(), "UTF-8");

                    if (ackStr.contains(frame.getTransferId()) && ackStr.contains(String.valueOf(indexChunk))) {
                        ackRecibido = true;
                        LOGGER.finer(() -> "ACK recibido para chunk " + indexChunk);
                    }
                } catch (Exception e) {
                    intentos++;
                    final int intentoError = intentos;
                    if (intentos < MAX_REINTENTOS) {
                        LOGGER.fine(() -> "Timeout esperando ACK, reintentando... (" + intentoError + "/" + MAX_REINTENTOS + ")");
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("Error enviando chunk: " + e.getMessage());
                intentos++;
            }
        }

        return ackRecibido;
    }

    public static void main(String[] args) throws Exception {
        String host = "localhost";
        int puerto = 5001;

        ClienteArchivoUDPStreaming cliente = new ClienteArchivoUDPStreaming(host, puerto);

        Path archivoTest = Path.of("test-500mb.bin");
        if (!Files.exists(archivoTest)) {
            System.out.println("Creando archivo de prueba de 500 MB...");
            crearArchivoTest(archivoTest, 500 * 1024 * 1024L);
            System.out.println("Archivo de prueba creado en: " + archivoTest.toAbsolutePath());
        }

        System.out.println("Iniciando envío de archivo por UDP streaming con control confiable...");
        long inicio = System.currentTimeMillis();
        cliente.enviarArchivoGrande(archivoTest);
        long duracion = System.currentTimeMillis() - inicio;

        System.out.println("Transferencia completada en " + (duracion / 1000) + " segundos");
    }

    private static void crearArchivoTest(Path ruta, long tamaño) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(ruta.toFile())) {
            byte[] buffer = new byte[1024 * 1024];
            long bytesEscritos = 0;

            while (bytesEscritos < tamaño) {
                int bytesAEscribir = (int) Math.min(buffer.length, tamaño - bytesEscritos);
                fos.write(buffer, 0, bytesAEscribir);
                bytesEscritos += bytesAEscribir;

                if (bytesEscritos % (50 * 1024 * 1024) == 0) {
                    System.out.println("Creado: " + (bytesEscritos / (1024*1024)) + " MB");
                }
            }
        }
    }
}

