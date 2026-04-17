package com.arquitectura;

import com.arquitectura.aplicacion.ProcesadorMensajes;
import com.arquitectura.aplicacion.RespuestaSender;
import com.arquitectura.infraestructura.logs.LogConfig;
import com.arquitectura.aplicacion.router.MensajeRouter;
import com.arquitectura.aplicacion.router.MensajeRouterFactory;
import com.arquitectura.comun.dto.PaqueteDatos;
import com.arquitectura.infraestructura.transporte.ProtocoloTransporte;
import com.arquitectura.infraestructura.transporte.ProtocoloTransporteFactory;

import java.io.*;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {

        LogConfig.configureRootLogger();

        Properties properties = new Properties();

        try (InputStream inputStream = Main.class.getClassLoader().getResourceAsStream("application.properties")) {

            properties.load(inputStream);

            String protocolo = properties.getProperty("transfer-protocol");
            int puerto = Integer.parseInt(properties.getProperty("server.port"));

            ProtocoloTransporte transporte = ProtocoloTransporteFactory.crear(protocolo);

            LOGGER.info(() -> "Servidor iniciado con protocolo: " + transporte.getNombre());

            transporte.iniciar(puerto);

            MensajeRouter router = MensajeRouterFactory.crearRouter();
            RespuestaSender sender = new RespuestaSender();
            ProcesadorMensajes procesador = new ProcesadorMensajes(router);

            while (true) {

                PaqueteDatos paquete = transporte.recibir();

                String respuesta = procesador.procesar(paquete.getData());

                sender.enviar(paquete, respuesta, transporte);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error en servidor", e);
        }
    }
}
