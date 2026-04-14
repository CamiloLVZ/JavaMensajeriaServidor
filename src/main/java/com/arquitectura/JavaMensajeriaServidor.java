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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JavaMensajeriaServidor {

    private static final Logger LOGGER = Logger.getLogger(JavaMensajeriaServidor.class.getName());

    public static void main(String[] args) {
        LogConfig.configureRootLogger();

        Properties properties = new Properties();

        try (InputStream inputStream = JavaMensajeriaServidor.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (inputStream == null) {
                throw new IllegalStateException("No se encontro application.properties en el classpath.");
            }

            properties.load(inputStream);
            String protocolo = properties.getProperty("transfer-protocol");
            int puerto = Integer.parseInt(properties.getProperty("server.port"));

            LOGGER.info(() -> "Configuracion cargada. Protocolo=" + protocolo + ", puerto=" + puerto);

            ProtocoloTransporte transporte = ProtocoloTransporteFactory.crear(protocolo);
            MensajeRouter router = MensajeRouterFactory.crearRouter();

            transporte.iniciar(puerto);
            LOGGER.info(() -> "Servidor escuchando en puerto " + puerto);

            while (true) {
                LOGGER.info("Esperando mensaje entrante");

                PaqueteDatos paquete = transporte.recibir();
                String json = new String(paquete.getData(), StandardCharsets.UTF_8);

                LOGGER.info(() -> "Mensaje recibido desde " + paquete.getHostOrigen() + ":" + paquete.getPuertoOrigen());
                LOGGER.fine(() -> "Payload JSON recibido: " + json);

                Mensaje<?> mensaje = JsonUtil.fromJson(json, Mensaje.class);
                LOGGER.info(() -> "Procesando accion " + mensaje.getAccion() + " de tipo " + mensaje.getTipo());

                Respuesta<?> respuesta = router.responder(mensaje);
                String jsonRespuesta = JsonUtil.toJson(respuesta);

                LOGGER.fine(() -> "Payload JSON respuesta: " + jsonRespuesta);

                transporte.enviar(jsonRespuesta.getBytes(StandardCharsets.UTF_8), paquete.getHostOrigen(), paquete.getPuertoOrigen());
                LOGGER.info(() -> "Respuesta enviada a " + paquete.getHostOrigen() + ":" + paquete.getPuertoOrigen());
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fallo fatal en el servidor", e);
        }
    }
}
