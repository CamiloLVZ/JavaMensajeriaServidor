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
                    GestorSesiones.getInstance().cerrarTodas();
                    ConexionMySql.cerrar();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error durante el apagado del servidor", e);
                }
            }));

            LOGGER.info(() -> "Servidor iniciado. Protocolo: " + transporte.getNombre() + " | Puerto: "+ puerto +" | max-clients=" + maxClientes);

            long requestCounter = 0;

            while (true) {
                // El transporte abstrae TCP/UDP. Siempre devolvemos el mismo DTO PaqueteDatos.
                PaqueteDatos paquete = transporte.recibir();
                requestCounter++;
                final long reqNum = requestCounter;

                // Intenta tomar una tarea del pool CON TIMEOUT.
                // Si no hay workers libres en 200ms, rechaza la conexión y vuelve a accept().
                // Esto NUNCA bloquea el hilo main indefinidamente.
                AtencionClienteTask tarea = poolTareas.tomar(200, TimeUnit.MILLISECONDS);

                if (tarea == null) {
                    // Capacidad agotada — cerrar la conexión del cliente sin bloquear el main.
                    LOGGER.warning(() -> "[Request #" + reqNum + "] Capacidad maxima alcanzada. Rechazando conexion. Sesiones activas: " + GestorSesiones.getInstance().sesionesActivas());
                    cerrarPaquete(paquete, transporte);
                    continue;
                }

                LOGGER.fine(() -> "[Request #" + reqNum + "] Tarea obtenida del pool, despachando a worker");

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

    /**
     * Cierra la conexión de un cliente rechazado por falta de capacidad.
     * Para TCP cierra el socket; para UDP simplemente se descarta el datagrama.
     */
    private static void cerrarPaquete(PaqueteDatos paquete, ProtocoloTransporte transporte) {
        try {
            if (paquete.getSocket() != null && !paquete.getSocket().isClosed()) {
                // Intentar enviar un error antes de cerrar (best-effort).
                try {
                    var writer = new java.io.BufferedWriter(
                            new java.io.OutputStreamWriter(paquete.getSocket().getOutputStream()));
                    writer.write("{\"estado\":\"ERROR\",\"error\":{\"codigo\":\"SERVIDOR_OCUPADO\",\"detalle\":\"Servidor a capacidad maxima, reintente luego\"}}");
                    writer.newLine();
                    writer.flush();
                } catch (Exception ignored) {
                    // Best-effort — si falla, simplemente cerramos.
                }
                paquete.getSocket().close();
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error cerrando paquete rechazado", e);
        }
    }
}
