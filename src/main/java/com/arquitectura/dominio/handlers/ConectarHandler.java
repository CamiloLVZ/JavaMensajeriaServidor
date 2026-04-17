package com.arquitectura.dominio.handlers;

import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadConectar;
import com.arquitectura.aplicacion.router.Handler;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Logger;

public class ConectarHandler implements Handler<PayloadConectar> {

    private static final Logger LOGGER = Logger.getLogger(ConectarHandler.class.getName());

    @Override
    public Respuesta<?> handle(Mensaje<PayloadConectar> mensaje) {

        PayloadConectar payload = mensaje.getPayload();
        LOGGER.info(() -> "Usuario conectado: " + payload.getUsername());

        Mensaje<String> mensajeRespuesta = new Mensaje<>();
        mensajeRespuesta.setTipo(TipoMensaje.RESPONSE);
        mensajeRespuesta.setAccion(Accion.CONECTAR);

        Metadata meta = new Metadata();
        meta.setIdMensaje(UUID.randomUUID().toString());
        meta.setTimestamp(LocalDateTime.now());

        mensajeRespuesta.setMetadata(meta);
        mensajeRespuesta.setPayload("Conexion exitosa");

        Respuesta<String> respuesta = new Respuesta<>();
        respuesta.setMensaje(mensajeRespuesta);
        respuesta.setEstado(Estado.EXITO);

        LOGGER.info(() -> "Respuesta de conexion generada para " + payload.getUsername());
        return respuesta;
    }

    @Override
    public Class<PayloadConectar> getPayloadClass() {
        return PayloadConectar.class;
    }
}
