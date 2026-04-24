package com.arquitectura.dominio.handlers;

import com.arquitectura.aplicacion.ContextoSolicitud;
import com.arquitectura.aplicacion.router.Handler;
import com.arquitectura.aplicacion.sesion.GestorSesiones;
import com.arquitectura.mensajeria.ErrorDetalle;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Estado;

import java.util.logging.Logger;

public class DesconectarHandler implements Handler<Object> {

    private static final Logger LOGGER = Logger.getLogger(DesconectarHandler.class.getName());
    private final GestorSesiones gestorSesiones = GestorSesiones.getInstance();

    @Override
    public Respuesta<?> handle(Mensaje<Object> mensaje) {
        String username = (mensaje.getMetadata() != null) ? mensaje.getMetadata().getClientId() : null;
        String endpoint = ContextoSolicitud.obtenerEndpointRemitente();
        String protocolo = ContextoSolicitud.obtenerProtocolo();

        if (username == null || username.isBlank()) {
            LOGGER.warning(() -> "Desconexion rechazada: username ausente desde " + endpoint);
            Respuesta<?> respuesta = new Respuesta<>();
            respuesta.setEstado(Estado.ERROR);
            respuesta.setError(new ErrorDetalle("USERNAME_INVALIDO", "El username es obligatorio para desconectarse"));
            return respuesta;
        }

        boolean eliminado = gestorSesiones.eliminar(username);

        if (eliminado) {
            LOGGER.info(() -> "Usuario desconectado: " + username
                    + " | endpoint=" + endpoint
                    + " | protocolo=" + protocolo
                    + " | sesionesActivas=" + gestorSesiones.sesionesActivas());
        } else {
            LOGGER.warning(() -> "Desconexion de usuario sin sesion activa: " + username
                    + " | endpoint=" + endpoint);
        }

        Respuesta<String> respuesta = new Respuesta<>();
        respuesta.setEstado(Estado.EXITO);
        return respuesta;
    }

    @Override
    public Class<Object> getPayloadClass() {
        return Object.class;
    }
}
