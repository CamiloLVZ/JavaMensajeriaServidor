package com.arquitectura.infraestructura.transporte;

import java.util.logging.Logger;

/**
 * Utilidad para facilitar el acceso al gestor de transferencias
 * desde diferentes partes de la aplicación.
 */
public class GestorTransferenciasUtil {

    private static final Logger LOGGER = Logger.getLogger(GestorTransferenciasUtil.class.getName());
    private static GestorTransferenciasStreaming instancia;

    /**
     * Inicializa el gestor de transferencias (llamar desde Main).
     */
    public static void inicializar(ProtocoloTransporte transporte) {
        try {
            if (transporte instanceof TcpProtocoloTransporte) {
                TcpProtocoloTransporte tcp = (TcpProtocoloTransporte) transporte;
                instancia = tcp.getGestorTransferencias();
            } else if (transporte instanceof UdpProtocoloTransporte) {
                UdpProtocoloTransporte udp = (UdpProtocoloTransporte) transporte;
                instancia = udp.getGestorTransferencias();
            }

            if (instancia != null) {
                LOGGER.info("Gestor de transferencias streaming inicializado");

                // Iniciar thread de limpieza de transferencias expiradas
                iniciarThreadLimpiezaAutomatica();
            }
        } catch (Exception e) {
            LOGGER.warning("No se pudo inicializar gestor de transferencias: " + e.getMessage());
        }
    }

    /**
     * Obtiene la instancia del gestor de transferencias.
     */
    public static GestorTransferenciasStreaming obtener() {
        if (instancia == null) {
            throw new IllegalStateException("Gestor de transferencias no inicializado. " +
                    "Llamar a GestorTransferenciasUtil.inicializar() desde Main.");
        }
        return instancia;
    }

    /**
     * Inicia un thread que limpia transferencias expiradas cada hora.
     */
    private static void iniciarThreadLimpiezaAutomatica() {
        Thread threadLimpieza = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(3600000); // Cada hora
                    if (instancia != null) {
                        LOGGER.fine("Ejecutando limpieza de transferencias expiradas...");
                        instancia.limpiarTransferenciasExpiradas();
                    }
                } catch (InterruptedException e) {
                    LOGGER.fine("Thread de limpieza interrumpido");
                    break;
                } catch (Exception e) {
                    LOGGER.warning("Error durante limpieza: " + e.getMessage());
                }
            }
        });

        threadLimpieza.setName("TransferenciasLimpiezaThread");
        threadLimpieza.setDaemon(true);
        threadLimpieza.start();
        LOGGER.info("Thread de limpieza automática iniciado");
    }

    /**
     * Detiene el gestor de transferencias.
     */
    public static void detener() {
        instancia = null;
        LOGGER.info("Gestor de transferencias detenido");
    }
}

