package com.arquitectura;

import com.arquitectura.aplicacion.ProcesadorMensajes;
import com.arquitectura.aplicacion.RespuestaSender;
import com.arquitectura.infraestructura.logs.LogConfig;
import com.arquitectura.aplicacion.router.MensajeRouter;
import com.arquitectura.aplicacion.router.MensajeRouterFactory;
import com.arquitectura.comun.dto.PaqueteDatos;
import com.arquitectura.infraestructura.persistencia.ConexionMySql;
import com.arquitectura.infraestructura.seguridad.CryptoConfig;
import com.arquitectura.infraestructura.transporte.ProtocoloTransporte;
import com.arquitectura.infraestructura.transporte.ProtocoloTransporteFactory;
import com.arquitectura.infraestructura.transporte.GestorTransferenciasUtil;

import java.io.*;
import java.util.Properties;
import java.util.logging.Logger;

public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {

        LogConfig.configureRootLogger();

        Properties properties = new Properties();

        try (InputStream inputStream = Main.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (inputStream == null) {
                throw new IllegalStateException("No se encontro application.properties en el classpath");
            }
            properties.load(inputStream);

            String protocolo = properties.getProperty("transfer-protocol");
            int puerto = Integer.parseInt(properties.getProperty("server.port"));

            CryptoConfig.configurar(properties);
            ConexionMySql.configurar(properties);
            // Comentar para evitar problemas con logging en BD
            // LogConfig.configureDatabaseLogging();
            Runtime.getRuntime().addShutdownHook(new Thread(ConexionMySql::cerrar));

            ProtocoloTransporte transporte = ProtocoloTransporteFactory.crear(protocolo);

            LOGGER.info(() -> "Servidor iniciado con protocolo: " + transporte.getNombre());

            transporte.iniciar(puerto);

            // Inicializar gestor de transferencias streaming
            GestorTransferenciasUtil.inicializar(transporte);
            Runtime.getRuntime().addShutdownHook(new Thread(GestorTransferenciasUtil::detener));

            MensajeRouter router = MensajeRouterFactory.crearRouter();
            RespuestaSender sender = new RespuestaSender();
            ProcesadorMensajes procesador = new ProcesadorMensajes(router);

            LOGGER.info("=".repeat(80));
            LOGGER.info("SERVIDOR INICIADO CORRECTAMENTE");
            LOGGER.info("Protocolo: " + transporte.getNombre());
            LOGGER.info("Puerto: " + puerto);
            LOGGER.info("Soporte de archivos >1GB: ✓ HABILITADO (Streaming)");
            LOGGER.info("=".repeat(80));

            while (true) {
                try {
                    PaqueteDatos paquete = transporte.recibir();
                    String respuesta = procesador.procesar(paquete);
                    sender.enviar(paquete, respuesta, transporte);
                } catch (Exception e) {
                    // Loguear solo en consola, no en BD para evitar ciclos de error
                    System.err.println("ERROR procesando mensaje: " + e.getMessage());
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            System.err.println("ERROR FATAL en servidor: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
