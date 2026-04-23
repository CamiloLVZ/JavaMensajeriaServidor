package com.arquitectura.infraestructura.transporte;

import com.arquitectura.aplicacion.transferencia.StreamEmisorTcp;
import com.arquitectura.aplicacion.transferencia.StreamReceptorTcp;
import com.arquitectura.comun.dto.PaqueteDatos;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Transporte TCP.
 *
 * Protocolo de detección en el primer byte de cada conexión:
 * - '{' (0x7B): mensaje JSON de control → flujo normal.
 * - 0x02 (STX): chunk de subida binario → StreamReceptorTcp.
 * - 0x03 (ETX): señal de descarga → StreamEmisorTcp.
 */
public class TcpProtocoloTransporte implements ProtocoloTransporte {

    private static final Logger LOGGER = Logger.getLogger(TcpProtocoloTransporte.class.getName());

    /** Señal de subida (cliente → servidor). */
    static final byte STREAM_SIGNAL_UPLOAD   = 0x02;

    /** Señal de descarga (cliente → servidor para pedir chunks). */
    static final byte STREAM_SIGNAL_DOWNLOAD = 0x03;

    private ServerSocket serverSocket;

    /** Hilo dedicado para operaciones de streaming (upload/download) — no bloquea el accept(). */
    private final ExecutorService streamingExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "tcp-streaming");
        t.setDaemon(true);
        return t;
    });

    private final StreamReceptorTcp streamReceptor = new StreamReceptorTcp();
    private final StreamEmisorTcp   streamEmisor   = new StreamEmisorTcp();

    @Override
    public void iniciar(int puerto) {
        try {
            serverSocket = new ServerSocket(puerto);
        } catch (Exception e) {
            throw new RuntimeException("Error iniciando TCP en puerto " + puerto, e);
        }
    }

    @Override
    public void enviar(byte[] datos, String hostDestino, int puertoDestino) {
        try (Socket socket = new Socket(hostDestino, puertoDestino);
             OutputStream output = socket.getOutputStream()) {
            output.write(datos);
            output.flush();
        } catch (Exception e) {
            throw new RuntimeException("Error enviando datos por TCP", e);
        }
    }

    @Override
    public PaqueteDatos recibir() {
        while (true) {
            try {
                Socket cliente = serverSocket.accept();
                PushbackInputStream pis = new PushbackInputStream(cliente.getInputStream());

                int primerByte = pis.read();
                if (primerByte == -1) {
                    cliente.close();
                    continue;
                }

                if ((byte) primerByte == STREAM_SIGNAL_UPLOAD) {
                    manejarSubidaTcp(cliente);
                    continue;
                }

                if ((byte) primerByte == STREAM_SIGNAL_DOWNLOAD) {
                    manejarDescargaTcp(cliente);
                    continue;
                }

                // JSON normal
                pis.unread(primerByte);
                BufferedReader reader = new BufferedReader(new InputStreamReader(pis, StandardCharsets.UTF_8));
                String json = reader.readLine();

                if (json == null || json.isBlank()) {
                    cliente.close();
                    continue;
                }

                return new PaqueteDatos(json.getBytes(StandardCharsets.UTF_8), cliente);

            } catch (Exception e) {
                if (serverSocket.isClosed()) {
                    throw new RuntimeException("ServerSocket cerrado", e);
                }
                LOGGER.warning(() -> "Error aceptando conexión TCP: " + e.getMessage());
            }
        }
    }

    private void manejarSubidaTcp(Socket cliente) {
        streamingExecutor.submit(() -> {
            try {
                streamReceptor.recibirArchivo(cliente);
            } catch (Exception e) {
                LOGGER.warning(() -> "Error en streaming de subida TCP: " + e.getMessage());
            } finally {
                cerrar(cliente);
            }
        });
    }

    private void manejarDescargaTcp(Socket cliente) {
        streamingExecutor.submit(() -> {
            try {
                streamEmisor.emitirArchivo(cliente);
            } catch (Exception e) {
                LOGGER.warning(() -> "Error en streaming de descarga TCP: " + e.getMessage());
            } finally {
                cerrar(cliente);
            }
        });
    }

    private void cerrar(Socket s) {
        try { if (!s.isClosed()) s.close(); } catch (Exception ignored) {}
    }

    @Override
    public void detener() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                LOGGER.info("Transporte TCP detenido");
            }
        } catch (Exception e) {
            throw new RuntimeException("Error cerrando TCP", e);
        } finally {
            streamingExecutor.shutdownNow();
        }
    }

    @Override
    public String getNombre() { return "TCP"; }
}
