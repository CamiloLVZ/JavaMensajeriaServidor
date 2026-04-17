package com.arquitectura.infraestructura.transporte;

import com.arquitectura.comun.dto.FrameTransferencia;
import com.arquitectura.comun.dto.PaqueteDatos;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;
import java.util.logging.Logger;

public class UdpProtocoloTransporte implements ProtocoloTransporte {

    private static final Logger LOGGER = Logger.getLogger(UdpProtocoloTransporte.class.getName());
    private static final int MAX_UDP_PAYLOAD = 65000; // Considerando overhead de UDP/IP
    private static final long ACK_TIMEOUT_MS = 5000;
    private static final int MAX_REINTENTOS = 5;

    private DatagramSocket socket;
    private boolean activo = false;
    private GestorTransferenciasStreaming gestorTransferencias;

    // Cache de chunks recibidos por transferencia (para detectar pérdida en UDP)
    private final Map<String, Set<Long>> chunksRecibidosPorTransferencia = Collections.synchronizedMap(
        new LinkedHashMap<>()
    );

    @Override
    public void iniciar(int puerto) {
        try {
            socket = new DatagramSocket(puerto);
            socket.setSoTimeout(30000); // 30 segundos timeout para recepción
            activo = true;
            gestorTransferencias = new GestorTransferenciasStreaming();
            LOGGER.info(() -> "Transporte UDP iniciado en puerto " + puerto);
        } catch (Exception e) {
            throw new RuntimeException("Error iniciando UDP", e);
        }
    }

    @Override
    public void enviar(byte[] datos, String hostDestino, int puertoDestino) {
        try {
            InetAddress direccion = InetAddress.getByName(hostDestino);
            DatagramPacket paquete = new DatagramPacket(datos, datos.length, direccion, puertoDestino);
            socket.send(paquete);
            LOGGER.info(() -> "Enviando " + datos.length + " bytes por UDP a " + hostDestino + ":" + puertoDestino);
        } catch (Exception e) {
            throw new RuntimeException("Error enviando datos por UDP", e);
        }
    }

    @Override
    public PaqueteDatos recibir() {
        try {
            byte[] buffer = new byte[MAX_UDP_PAYLOAD];
            DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);

            socket.receive(paquete);

            byte[] datos = new byte[paquete.getLength()];
            System.arraycopy(paquete.getData(), 0, datos, 0, paquete.getLength());

            // Intentar interpretar como frame de transferencia streaming
            FrameTransferencia frame = intentarDeserializarFrame(datos);
            if (frame != null) {
                // Es una transferencia streaming por UDP
                boolean resultado = gestorTransferencias.procesarChunk(frame);

                // Registrar que recibimos este chunk
                chunksRecibidosPorTransferencia
                    .computeIfAbsent(frame.getTransferId(), k -> Collections.synchronizedSet(new HashSet<>()))
                    .add(frame.getIndexChunk());

                // Enviar ACK al remitente
                enviarAckChunk(frame.getTransferId(), frame.getIndexChunk(),
                              paquete.getAddress().getHostAddress(), paquete.getPort());

                if (!resultado) {
                    LOGGER.warning(() -> "Error procesando chunk UDP de transferencia: " + frame.getTransferId());
                }

                // Devolver dato vacío para que continúe procesando
                return new PaqueteDatos(
                    new byte[0],
                    paquete.getAddress().getHostAddress(),
                    paquete.getPort()
                );
            }

            // Si no es frame, devolver como JSON tradicional
            return new PaqueteDatos(
                    datos,
                    paquete.getAddress().getHostAddress(),
                    paquete.getPort()
            );

        } catch (Exception e) {
            throw new RuntimeException("Error recibiendo datos por UDP", e);
        }
    }

    /**
     * Intenta deserializar un frame de transferencia desde datos UDP.
     */
    private FrameTransferencia intentarDeserializarFrame(byte[] datos) {
        try {
            // Validar longitud mínima
            if (datos.length < 50) return null;

            // Intentar deserializar
            FrameTransferencia frame = FrameTransferencia.deserializar(datos);
            return frame;
        } catch (Exception e) {
            // No es un frame válido, probablemente sea JSON
            return null;
        }
    }

    /**
     * Envía un ACK (confirmación) de recepción de chunk.
     */
    private void enviarAckChunk(String transferId, long indexChunk, String hostDestino, int puertoDestino) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeUTF("ACK");
            dos.writeUTF(transferId);
            dos.writeLong(indexChunk);
            dos.flush();

            byte[] ackData = baos.toByteArray();
            InetAddress direccion = InetAddress.getByName(hostDestino);
            DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, direccion, puertoDestino);
            socket.send(ackPacket);

            LOGGER.finer(() -> "ACK enviado para chunk " + indexChunk + " de " + transferId);

        } catch (Exception e) {
            LOGGER.warning(() -> "Error enviando ACK para chunk " + indexChunk + ": " + e.getMessage());
        }
    }

    /**
     * Obtiene los chunks recibidos para una transferencia (para detectar pérdidas).
     */
    public Set<Long> obtenerChunksRecibidos(String transferId) {
        return chunksRecibidosPorTransferencia.getOrDefault(transferId, Collections.emptySet());
    }

    /**
     * Envía un frame de transferencia binaria por UDP (con reintentos y ACK).
     */
    public void enviarFrameTransferenciaConAck(FrameTransferencia frame, String hostDestino, int puertoDestino) {
        try {
            byte[] frameSerialized = frame.serializar();
            InetAddress direccion = InetAddress.getByName(hostDestino);

            boolean ackRecibido = false;
            int intentos = 0;

            while (!ackRecibido && intentos < MAX_REINTENTOS) {
                // Enviar frame
                DatagramPacket paquete = new DatagramPacket(frameSerialized, frameSerialized.length,
                                                            direccion, puertoDestino);
                socket.send(paquete);

                final int intentoActual = intentos;
                LOGGER.finer(() -> "Enviando frame UDP (intento " + (intentoActual + 1) + ") " +
                                   "para transferencia " + frame.getTransferId() +
                                   " chunk " + frame.getIndexChunk());

                // Esperar ACK
                try {
                    byte[] bufferAck = new byte[256];
                    DatagramPacket ackPacket = new DatagramPacket(bufferAck, bufferAck.length);
                    socket.setSoTimeout((int) ACK_TIMEOUT_MS);
                    socket.receive(ackPacket);

                    if (esAckValido(bufferAck, ackPacket.getLength(), frame.getTransferId(), frame.getIndexChunk())) {
                        ackRecibido = true;
                        LOGGER.finer(() -> "ACK recibido para chunk " + frame.getIndexChunk());
                    }
                } catch (Exception e) {
                    intentos++;
                    final int intentoActualError = intentos;
                    LOGGER.fine(() -> "Timeout esperando ACK, reintentando... (" + intentoActualError + "/" + MAX_REINTENTOS + ")");
                }
            }

            if (!ackRecibido) {
                LOGGER.warning(() -> "No se recibió ACK para chunk " + frame.getIndexChunk() +
                                     " después de " + MAX_REINTENTOS + " intentos");
            }

        } catch (Exception e) {
            throw new RuntimeException("Error enviando frame de transferencia por UDP", e);
        }
    }

    private boolean esAckValido(byte[] ackData, int length, String transferIdEsperado, long chunkEsperado) {
        try (DataInputStream dis = new DataInputStream(new java.io.ByteArrayInputStream(ackData, 0, length))) {
            String tipo = dis.readUTF();
            String transferId = dis.readUTF();
            long chunk = dis.readLong();
            return "ACK".equals(tipo) &&
                   transferIdEsperado.equals(transferId) &&
                   chunkEsperado == chunk;
        } catch (Exception e) {
            return false;
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
        if (socket != null && !socket.isClosed()) {
            socket.close();
            LOGGER.info("Transporte UDP detenido");
        }
    }

    @Override
    public String getNombre() {
        return "UDP";
    }

}
