package com.arquitectura.dominio.handlers;

import com.arquitectura.aplicacion.router.Handler;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadEnviarArchivo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Logger;

public class EnviarArchivoHandler implements Handler<PayloadEnviarArchivo> {

    private static final Logger LOGGER = Logger.getLogger(EnviarArchivoHandler.class.getName());
    private static final Path DIRECTORIO_DESTINO = Path.of("archivos-recibidos");

    @Override
    public Respuesta<?> handle(Mensaje<PayloadEnviarArchivo> mensaje) {

        PayloadEnviarArchivo payload = mensaje.getPayload();
        String remitente = resolverRemitente(mensaje);

        try {
            Path rutaArchivo = guardarArchivo(payload);

            LOGGER.info(() -> "Archivo recibido: " + payload.getNombre() + " desde " + remitente);
            System.out.println("[SERVIDOR] Archivo recibido de " + remitente + ": " + rutaArchivo.toAbsolutePath());

            return crearRespuestaExitosa(payload.getNombre(), rutaArchivo);
        } catch (IOException e) {
            LOGGER.severe(() -> "No fue posible guardar el archivo " + payload.getNombre() + ": " + e.getMessage());
            throw new IllegalStateException("No fue posible guardar el archivo recibido", e);
        }
    }

    @Override
    public Class<PayloadEnviarArchivo> getPayloadClass() {
        return PayloadEnviarArchivo.class;
    }

    private Path guardarArchivo(PayloadEnviarArchivo payload) throws IOException {
        Files.createDirectories(DIRECTORIO_DESTINO);

        String nombreArchivo = construirNombreArchivo(payload);
        Path rutaArchivo = DIRECTORIO_DESTINO.resolve(nombreArchivo);

        Files.write(rutaArchivo, obtenerContenido(payload));
        return rutaArchivo;
    }

    private String construirNombreArchivo(PayloadEnviarArchivo payload) {
        String extension = payload.getExtension();
        String nombre = payload.getNombre();

        if (extension == null || extension.isBlank() || nombre.endsWith("." + extension)) {
            return nombre;
        }

        return nombre + "." + extension;
    }

    private byte[] obtenerContenido(PayloadEnviarArchivo payload) {
        String contenido = payload.getContenido();

        try {
            return Base64.getDecoder().decode(contenido);
        } catch (IllegalArgumentException e) {
            return contenido.getBytes(StandardCharsets.UTF_8);
        }
    }

    private Respuesta<String> crearRespuestaExitosa(String nombreArchivo, Path rutaArchivo) {
        Mensaje<String> mensajeRespuesta = new Mensaje<>();
        mensajeRespuesta.setTipo(TipoMensaje.RESPONSE);
        mensajeRespuesta.setAccion(Accion.ENVIAR_DOCUMENTO);
        mensajeRespuesta.setMetadata(crearMetadataRespuesta());
        mensajeRespuesta.setPayload("Archivo recibido: " + nombreArchivo + " en " + rutaArchivo.toAbsolutePath());

        Respuesta<String> respuesta = new Respuesta<>();
        respuesta.setEstado(Estado.EXITO);
        respuesta.setMensaje(mensajeRespuesta);
        return respuesta;
    }

    private Metadata crearMetadataRespuesta() {
        Metadata metadata = new Metadata();
        metadata.setIdMensaje(UUID.randomUUID().toString());
        metadata.setTimestamp(LocalDateTime.now());
        return metadata;
    }

    private String resolverRemitente(Mensaje<PayloadEnviarArchivo> mensaje) {
        if (mensaje.getMetadata() != null && mensaje.getMetadata().getClientId() != null) {
            return mensaje.getMetadata().getClientId();
        }

        return "desconocido";
    }
}
