package com.arquitectura.transporte;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Logger;

public class TcpProtocoloTransporte implements ProtocoloTransporte {

    private static final Logger LOGGER = Logger.getLogger(TcpProtocoloTransporte.class.getName());

    private ServerSocket serverSocket;
    private boolean activo = false;

    @Override
    public void iniciar(int puerto) {
        try {
            serverSocket = new ServerSocket(puerto);
            activo = true;
            LOGGER.info(() -> "Transporte TCP iniciado en puerto " + puerto);
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
        try (Socket cliente = serverSocket.accept();
             InputStream input = cliente.getInputStream()) {

            LOGGER.info(() -> "Conexion TCP aceptada desde " + cliente.getInetAddress().getHostAddress() + ":" + cliente.getPort());
            byte[] buffer = input.readAllBytes();
            LOGGER.info(() -> "Se recibieron " + buffer.length + " bytes por TCP");

            return new PaqueteDatos(buffer, cliente.getInetAddress().getHostAddress(), cliente.getPort());

        } catch (Exception e) {
            throw new RuntimeException("Error recibiendo datos por TCP", e);
        }
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
}
