package com.arquitectura.dominio.handlers;

import com.arquitectura.aplicacion.ContextoSolicitud;
import com.arquitectura.aplicacion.router.Handler;
import com.arquitectura.aplicacion.sesion.GestorSesiones;
import com.arquitectura.dominio.repositorios.MensajeRepository;
import com.arquitectura.dominio.repositorios.JpaMensajeRepository;
import com.arquitectura.infraestructura.seguridad.CryptoUtil;
import com.arquitectura.mensajeria.ErrorDetalle;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadEnviarMensaje;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Logger;

public class MensajeTextoHandler implements Handler<PayloadEnviarMensaje> {

    private static final Logger LOGGER = Logger.getLogger(MensajeTextoHandler.class.getName());
    private final MensajeRepository mensajeRepository = new JpaMensajeRepository();
    private final GestorSesiones gestorSesiones = GestorSesiones.getInstance();

    @Override
    public Respuesta<?> handle(Mensaje<PayloadEnviarMensaje> mensaje) {

        PayloadEnviarMensaje payload = mensaje.getPayload();
        String remitente = resolverRemitente(mensaje, payload);
        if (!gestorSesiones.existeSesionActiva(remitente)) {
            return crearErrorSesionNoRegistrada(remitente);
        }
        gestorSesiones.marcarActividad(remitente);

        String ipRemitente = ContextoSolicitud.obtenerIpRemitente();
        String texto = payload.getContenido();
        LocalDateTime fechaEnvio = resolverFecha(mensaje);
        String mensajeId = resolverMensajeId(mensaje);
        byte[] contenidoBytes = texto.getBytes(StandardCharsets.UTF_8);
        String hashSha256 = CryptoUtil.sha256Base64(contenidoBytes);
        String contenidoCifrado = CryptoUtil.aesEncryptBase64(contenidoBytes);

        mensajeRepository.guardar(mensajeId, remitente, ipRemitente, texto, hashSha256, contenidoCifrado, fechaEnvio);

        LOGGER.info(() -> "Mensaje de texto recibido | Remitente: %s | IP: %s | Hash: %s "
                .formatted(remitente, ipRemitente, hashSha256));

        System.out.println("[SERVIDOR] Mensaje recibido de " + remitente + " (" + ipRemitente + "): " + texto);

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
        if (mensaje.getMetadata() != null && mensaje.getMetadata().getClientId() != null) {
            return mensaje.getMetadata().getClientId();
        }

        if (payload != null && payload.getAutor() != null && !payload.getAutor().isBlank()) {
            return payload.getAutor();
        }

        return "desconocido";
    }

    private LocalDateTime resolverFecha(Mensaje<PayloadEnviarMensaje> mensaje) {
        if (mensaje.getMetadata() != null && mensaje.getMetadata().getTimestamp() != null) {
            return mensaje.getMetadata().getTimestamp();
        }

        return LocalDateTime.now();
    }

    private String resolverMensajeId(Mensaje<PayloadEnviarMensaje> mensaje) {
        if (mensaje.getMetadata() != null && mensaje.getMetadata().getIdMensaje() != null) {
            String idMensaje = mensaje.getMetadata().getIdMensaje();
            try {
                return UUID.fromString(idMensaje).toString();
            } catch (IllegalArgumentException ignored) {
                LOGGER.warning(() -> "idMensaje recibido no es UUID valido, se generara uno nuevo");
            }
        }

        return UUID.randomUUID().toString();
    }

    private Respuesta<?> crearErrorSesionNoRegistrada(String remitente) {
        Respuesta<?> respuesta = new Respuesta<>();
        respuesta.setEstado(Estado.ERROR);
        respuesta.setError(new ErrorDetalle(
                "SESION_NO_REGISTRADA",
                "El usuario [" + remitente + "] no tiene una sesion activa. Primero debe registrarse."
        ));
        return respuesta;
    }

}
