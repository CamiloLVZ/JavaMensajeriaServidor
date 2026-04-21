package com.arquitectura;

import com.arquitectura.aplicacion.ProcesadorMensajes;
import com.arquitectura.aplicacion.RespuestaSender;
import com.arquitectura.aplicacion.concurrencia.AtencionClienteTask;
import com.arquitectura.aplicacion.router.MensajeRouter;
import com.arquitectura.aplicacion.router.MensajeRouterFactory;
import com.arquitectura.aplicacion.sesion.GestorSesiones;
import com.arquitectura.comun.dto.PaqueteDatos;
import com.arquitectura.infraestructura.concurrencia.ObjectPool;
import com.arquitectura.infraestructura.logs.LogConfig;
import com.arquitectura.infraestructura.persistencia.ConexionMySql;
import com.arquitectura.infraestructura.seguridad.CryptoConfig;
import com.arquitectura.infraestructura.transporte.ProtocoloTransporte;
import com.arquitectura.infraestructura.transporte.ProtocoloTransporteFactory;

import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
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
            int maxClientes = Integer.parseInt(properties.getProperty("max-clients", "10"));
            long sessionTimeoutMinutos = Long.parseLong(properties.getProperty("session.timeout.minutes", "30"));

            CryptoConfig.configurar(properties);
            ConexionMySql.configurar(properties);
            LogConfig.configureDatabaseLogging();

            ProtocoloTransporte transporte = ProtocoloTransporteFactory.crear(protocolo);
            transporte.iniciar(puerto);

            MensajeRouter router = MensajeRouterFactory.crearRouter();
            RespuestaSender sender = new RespuestaSender();
            ProcesadorMensajes procesador = new ProcesadorMensajes(router);
            GestorSesiones.getInstance().configurar(maxClientes, Duration.ofMinutes(sessionTimeoutMinutos));

            // Pool fijo de hilos: limita cuántos clientes se atienden en paralelo.
            ExecutorService ejecutorClientes = Executors.newFixedThreadPool(maxClientes);

            // Object Pool de tareas reutilizables, también acotado por max-clients.
            ObjectPool<AtencionClienteTask> poolTareas = new ObjectPool<>(maxClientes, AtencionClienteTask::new);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    transporte.detener();
                    ejecutorClientes.shutdown();
                    if (!ejecutorClientes.awaitTermination(5, TimeUnit.SECONDS)) {
                        ejecutorClientes.shutdownNow();
                    }
                    ConexionMySql.cerrar();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error durante el apagado del servidor", e);
                }
            }));

            LOGGER.info(() -> "Servidor iniciado. Protocolo: " + transporte.getNombre() + " | Puerto: "+ puerto +" | max-clients=" + maxClientes);

            while (true) {
                // El transporte abstrae TCP/UDP. Siempre devolvemos el mismo DTO PaqueteDatos.
                PaqueteDatos paquete = transporte.recibir();

                // Si no hay tareas libres en el pool, esperamos. Esto impone el límite máximo.
                AtencionClienteTask tarea = poolTareas.tomar();

                // Se prepara la tarea con el request actual y su retorno al Object Pool.
                tarea.preparar(paquete, procesador, sender, transporte, () -> poolTareas.devolver(tarea));

                // Ejecución concurrente de la atención del cliente.
                ejecutorClientes.execute(tarea);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.SEVERE, "Servidor interrumpido", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error en servidor", e);
        }
    }
}
