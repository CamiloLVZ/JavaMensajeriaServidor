package com.arquitectura.dominio.handlers;

import com.arquitectura.aplicacion.router.Handler;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadEnviarMensaje;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Logger;

public class MensajeTextoHandler implements Handler<PayloadEnviarMensaje> {

    private static final Logger LOGGER = Logger.getLogger(MensajeTextoHandler.class.getName());

    @Override
    public Respuesta<?> handle(Mensaje<PayloadEnviarMensaje> mensaje) {

        PayloadEnviarMensaje payload = mensaje.getPayload();
        String remitente = resolverRemitente(mensaje, payload);
        String texto = payload.getContenido();

        LOGGER.info(() -> "Mensaje de texto recibido | Remitente: %s | Contenido: %s ".formatted(remitente, texto));

        System.out.println("[SERVIDOR] Mensaje recibido de " + remitente + ": " + texto);

        Mensaje<String> mensajeRespuesta = new Mensaje<>();
        mensajeRespuesta.setTipo(TipoMensaje.RESPONSE);
        mensajeRespuesta.setAccion(Accion.ENVIAR_MENSAJE);
        mensajeRespuesta.setMetadata(crearMetadataRespuesta());
        mensajeRespuesta.setPayload("Mensaje recibido correctamente");

        Respuesta<String> respuesta = new Respuesta<>();
        respuesta.setEstado(Estado.EXITO);
        respuesta.setMensaje(mensajeRespuesta);

        return respuesta;
    }

    @Override
    public Class<PayloadEnviarMensaje> getPayloadClass() {
        return PayloadEnviarMensaje.class;
    }

    private Metadata crearMetadataRespuesta() {
        Metadata metadata = new Metadata();
        metadata.setIdMensaje(UUID.randomUUID().toString());
        metadata.setTimestamp(LocalDateTime.now());
        return metadata;
    }

    private String resolverRemitente(Mensaje<PayloadEnviarMensaje> mensaje, PayloadEnviarMensaje payload) {
        if (payload != null && payload.getAutor() != null && !payload.getAutor().isBlank()) {
            return payload.getAutor();
        }

        if (mensaje.getMetadata() != null && mensaje.getMetadata().getClientId() != null) {
            return mensaje.getMetadata().getClientId();
        }

        return "desconocido";
    }
}
