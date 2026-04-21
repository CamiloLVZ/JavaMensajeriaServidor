package com.arquitectura.dominio.handlers;

import com.arquitectura.aplicacion.ContextoSolicitud;
import com.arquitectura.aplicacion.router.Handler;
import com.arquitectura.aplicacion.sesion.GestorSesiones;
import com.arquitectura.aplicacion.sesion.ResultadoRegistroSesion;
import com.arquitectura.mensajeria.ErrorDetalle;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadConectar;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Logger;

public class ConectarHandler implements Handler<PayloadConectar> {

    private static final Logger LOGGER = Logger.getLogger(ConectarHandler.class.getName());
    private final GestorSesiones gestorSesiones = GestorSesiones.getInstance();

    @Override
    public Respuesta<?> handle(Mensaje<PayloadConectar> mensaje) {
        PayloadConectar payload = mensaje.getPayload();
        String username = payload != null ? payload.getUsername() : null;
        String endpoint = ContextoSolicitud.obtenerEndpointRemitente();
        String protocolo = ContextoSolicitud.obtenerProtocolo();

        ResultadoRegistroSesion registro = gestorSesiones.registrar(username, endpoint, protocolo);
        if (!registro.exito()) {
            LOGGER.warning(() -> "Registro rechazado para [" + username + "] desde " + endpoint
                    + ". Motivo: " + registro.mensaje());
            return crearError(registro.codigoError(), registro.mensaje());
        }

        LOGGER.info(() -> "Usuario conectado: " + registro.sesion().getUsername()
                + " | endpoint=" + registro.sesion().getEndpoint()
                + " | protocolo=" + registro.sesion().getProtocolo()
                + " | sesionesActivas=" + gestorSesiones.sesionesActivas());

        Mensaje<String> mensajeRespuesta = new Mensaje<>();
        mensajeRespuesta.setTipo(TipoMensaje.RESPONSE);
        mensajeRespuesta.setAccion(Accion.CONECTAR);

        Metadata metadata = new Metadata();
        metadata.setIdMensaje(UUID.randomUUID().toString());
        metadata.setTimestamp(LocalDateTime.now());
        metadata.setClientId(registro.sesion().getUsername());
        mensajeRespuesta.setMetadata(metadata);
        mensajeRespuesta.setPayload(registro.mensaje() + ": " + registro.sesion().getUsername());

        Respuesta<String> respuesta = new Respuesta<>();
        respuesta.setMensaje(mensajeRespuesta);
        respuesta.setEstado(Estado.EXITO);

        LOGGER.info(() -> "Respuesta de conexion generada para " + registro.sesion().getUsername()
                + (registro.reconexion() ? " (reconexion)" : ""));
        return respuesta;
    }

    @Override
    public Class<PayloadConectar> getPayloadClass() {
        return PayloadConectar.class;
    }

    private Respuesta<?> crearError(String codigo, String detalle) {
        Respuesta<?> respuesta = new Respuesta<>();
        respuesta.setEstado(Estado.ERROR);
        respuesta.setError(new ErrorDetalle(codigo, detalle));
        return respuesta;
    }
}
