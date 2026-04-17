package com.arquitectura.transporte;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.logging.Logger;

public class UdpProtocoloTransporte implements ProtocoloTransporte {

    private static final Logger LOGGER = Logger.getLogger(UdpProtocoloTransporte.class.getName());

    private DatagramSocket socket;
    private boolean activo = false;

    @Override
    public void iniciar(int puerto) {
        try {
            socket = new DatagramSocket(puerto);
            activo = true;
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
            byte[] buffer = new byte[65535];
            DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);

            socket.receive(paquete);

            byte[] datos = new byte[paquete.getLength()];
            System.arraycopy(paquete.getData(), 0, datos, 0, paquete.getLength());

            return new PaqueteDatos(
                    datos,
                    paquete.getAddress().getHostAddress(),
                    paquete.getPort()
            );

        } catch (Exception e) {
            throw new RuntimeException("Error recibiendo datos por UDP", e);
        }
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
