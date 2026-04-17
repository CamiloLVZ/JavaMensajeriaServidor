package com.arquitectura.infraestructura.transporte;

import com.arquitectura.comun.dto.PaqueteDatos;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
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

        try {
            Socket cliente = serverSocket.accept();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(cliente.getInputStream())
            );

            String json = reader.readLine();

            return new PaqueteDatos(
                    json.getBytes(StandardCharsets.UTF_8),
                    cliente
            );

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

    @Override
    public String getNombre() {
        return "TCP";
    }
}
