package com.arquitectura.aplicacion.concurrencia;

import com.arquitectura.aplicacion.ProcesadorMensajes;
import com.arquitectura.aplicacion.RespuestaSender;
import com.arquitectura.comun.dto.PaqueteDatos;
import com.arquitectura.infraestructura.transporte.ProtocoloTransporte;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tarea reutilizable que atiende 1 cliente/solicitud.
 *
 * <p>Esta clase se reutiliza mediante Object Pool: en vez de crear un Runnable nuevo
 * por cada cliente, se "presta" una instancia, se ejecuta y luego se devuelve al pool.</p>
 */
public class AtencionClienteTask implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(AtencionClienteTask.class.getName());

    private PaqueteDatos paquete;
    private ProcesadorMensajes procesador;
    private RespuestaSender sender;
    private ProtocoloTransporte transporte;

    /**
     * Callback para devolver esta tarea al pool al finalizar.
     */
    private Runnable onFinish;

    /**
     * Carga en la tarea todos los datos de la solicitud actual.
     */
    public void preparar(PaqueteDatos paquete,
                         ProcesadorMensajes procesador,
                         RespuestaSender sender,
                         ProtocoloTransporte transporte,
                         Runnable onFinish) {
        this.paquete = paquete;
        this.procesador = procesador;
        this.sender = sender;
        this.transporte = transporte;
        this.onFinish = onFinish;
    }

    @Override
    public void run() {
        try {
            String respuesta = procesador.procesar(paquete);
            sender.enviar(paquete, respuesta, transporte);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error atendiendo cliente", e);
        } finally {
            limpiarEstado();

            if (onFinish != null) {
                onFinish.run();
            }
        }
    }

    /**
     * Limpia referencias para que la instancia quede lista para la próxima solicitud.
     */
    private void limpiarEstado() {
        if (paquete != null && paquete.getSocket() != null && !paquete.getSocket().isClosed()) {
            try {
                paquete.getSocket().close();
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "No se pudo cerrar el socket del cliente", e);
            }
        }

        paquete = null;
        procesador = null;
        sender = null;
        transporte = null;
        onFinish = null;
    }
}
