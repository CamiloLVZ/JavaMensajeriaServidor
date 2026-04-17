package com.arquitectura;

import com.arquitectura.infraestructura.JsonUtil;
import com.arquitectura.infraestructura.LogConfig;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.router.MensajeRouter;
import com.arquitectura.router.MensajeRouterFactory;
import com.arquitectura.transporte.PaqueteDatos;
import com.arquitectura.transporte.ProtocoloTransporte;
import com.arquitectura.transporte.ProtocoloTransporteFactory;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JavaMensajeriaServidor {

    private static final Logger LOGGER = Logger.getLogger(JavaMensajeriaServidor.class.getName());

    public static void main(String[] args) {

        LogConfig.configureRootLogger();

        Properties properties = new Properties();

        try (InputStream inputStream =
                     JavaMensajeriaServidor.class
                             .getClassLoader()
                             .getResourceAsStream("application.properties")) {

            properties.load(inputStream);

            String protocolo = properties.getProperty("transfer-protocol");
            int puerto = Integer.parseInt(properties.getProperty("server.port"));

            ProtocoloTransporte transporte = ProtocoloTransporteFactory.crear(protocolo);

            LOGGER.info(() -> "Servidor iniciado con protocolo: "
                    + transporte.getNombre());

            transporte.iniciar(puerto);

            MensajeRouter router = MensajeRouterFactory.crearRouter();

            while (true) {

                LOGGER.info("Esperando mensaje...");

                PaqueteDatos paquete = transporte.recibir();

                String json = new String(
                        paquete.getData(),
                        StandardCharsets.UTF_8
                );

                LOGGER.info(() -> "Mensaje recibido");

                Mensaje<?> mensaje =
                        JsonUtil.fromJson(json, Mensaje.class);

                Respuesta<?> respuesta =
                        router.responder(mensaje);

                String jsonRespuesta =
                        JsonUtil.toJson(respuesta);

                // 🔥 DIFERENCIA CLAVE TCP vs UDP

                if (paquete.getSocket() != null) {
                    // 👉 TCP
                    Socket socket = paquete.getSocket();

                    BufferedWriter writer = new BufferedWriter(
                            new OutputStreamWriter(socket.getOutputStream())
                    );

                    writer.write(jsonRespuesta);
                    writer.newLine();
                    writer.flush();

                    socket.close();

                    LOGGER.info("Respuesta enviada via TCP");

                } else {
                    // 👉 UDP
                    transporte.enviar(
                            jsonRespuesta.getBytes(StandardCharsets.UTF_8),
                            paquete.getHostOrigen(),
                            paquete.getPuertoOrigen()
                    );

                    LOGGER.info("Respuesta enviada via UDP");
                }
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error en servidor", e);
        }
    }
}
