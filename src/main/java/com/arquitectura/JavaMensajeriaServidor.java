package com.arquitectura;

import com.arquitectura.infraestructura.JsonUtil;
import com.arquitectura.infraestructura.LogConfig;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.router.MensajeRouter;
import com.arquitectura.router.MensajeRouterFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JavaMensajeriaServidor {

    private static final Logger LOGGER = Logger.getLogger(JavaMensajeriaServidor.class.getName());

    public static void main(String[] args) {
        LogConfig.configureRootLogger();

        int puerto = 8080;

        try (ServerSocket serverSocket = new ServerSocket(puerto)) {
            LOGGER.info(() -> "Servidor escuchando en puerto " + puerto);

            MensajeRouter router = MensajeRouterFactory.crearRouter();

            while (true) {
                LOGGER.info("Esperando cliente...");

                try (Socket cliente = serverSocket.accept();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(cliente.getInputStream(), StandardCharsets.UTF_8));
                     BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(cliente.getOutputStream(), StandardCharsets.UTF_8))) {

                    LOGGER.info(() -> "Cliente conectado desde " + cliente.getInetAddress().getHostAddress() + ":" + cliente.getPort());

                    String json = reader.readLine();
                    if (json == null || json.isBlank()) {
                        LOGGER.warning(() -> "Se recibio un mensaje vacio desde "
                                + cliente.getInetAddress().getHostAddress() + ":" + cliente.getPort());
                        continue;
                    }

                    LOGGER.info("JSON recibido del cliente");
                    LOGGER.fine(() -> "Payload recibido: " + json);

                    Mensaje<?> mensaje = JsonUtil.fromJson(json, Mensaje.class);
                    LOGGER.info(() -> "Accion recibida: " + mensaje.getAccion());

                    Respuesta<?> respuesta = router.responder(mensaje);
                    String jsonRespuesta = JsonUtil.toJson(respuesta);

                    LOGGER.info("Respuesta generada");
                    LOGGER.fine(() -> "Payload respuesta: " + jsonRespuesta);

                    writer.write(jsonRespuesta);
                    writer.newLine();
                    writer.flush();

                    LOGGER.info(() -> "Respuesta enviada a "
                            + cliente.getInetAddress().getHostAddress() + ":" + cliente.getPort());
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error atendiendo cliente", e);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fallo fatal en el servidor", e);
        }
    }
}
