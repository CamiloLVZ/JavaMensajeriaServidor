package com.arquitectura.transporte;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class TcpProtocoloTransporte implements ProtocoloTransporte {

    private ServerSocket serverSocket;
    private boolean activo = false;

    @Override
    public void iniciar(int puerto) {
        try {
            serverSocket = new ServerSocket(puerto);
            activo = true;
        } catch (Exception e) {
            throw new RuntimeException("Error iniciando TCP", e);
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
        try {
            Socket cliente = serverSocket.accept(); // BLOQUEANTE

            InputStream input = cliente.getInputStream();

            byte[] buffer = input.readAllBytes(); // simplificado

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
            }
        } catch (Exception e) {
            throw new RuntimeException("Error cerrando TCP", e);
        }
    }
}