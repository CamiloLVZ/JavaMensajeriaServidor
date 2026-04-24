package com.arquitectura.dominio.handlers;

import com.arquitectura.aplicacion.router.Handler;
import com.arquitectura.aplicacion.sesion.GestorSesiones;
import com.arquitectura.aplicacion.sesion.SesionCliente;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.TipoMensaje;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class ListarClientesHandler implements Handler<Object> {

    private static final Logger LOGGER = Logger.getLogger(ListarClientesHandler.class.getName());

    @Override
    public Respuesta<?> handle(Mensaje<Object> mensaje) {
        try {
            Collection<SesionCliente> sesiones = GestorSesiones.getInstance().listarSesiones();

            List<Map<String, Object>> resultado = new ArrayList<>();
            for (SesionCliente s : sesiones) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", s.getUsername());
                map.put("name", s.getUsername());
                map.put("ip", s.getIpRemitente());
                map.put("port", s.getPuertoRemitente());
                map.put("status", "Conectado");
                resultado.add(map);
            }

            LOGGER.info(() -> "Listando clientes: %d sesiones activas".formatted(resultado.size()));

            Mensaje<List<Map<String, Object>>> mensajeRespuesta = new Mensaje<>();
            mensajeRespuesta.setTipo(TipoMensaje.RESPONSE);
            mensajeRespuesta.setAccion(Accion.LISTAR_CLIENTES);
            mensajeRespuesta.setMetadata(crearMetadata());
            mensajeRespuesta.setPayload(resultado);

            Respuesta<List<Map<String, Object>>> respuesta = new Respuesta<>();
            respuesta.setEstado(Estado.EXITO);
            respuesta.setMensaje(mensajeRespuesta);

            return respuesta;

        } catch (Exception e) {
            LOGGER.severe(() -> "Error al listar clientes: " + e.getMessage());

            Mensaje<String> mensajeError = new Mensaje<>();
            mensajeError.setTipo(TipoMensaje.RESPONSE);
            mensajeError.setAccion(Accion.LISTAR_CLIENTES);
            mensajeError.setMetadata(crearMetadata());
            mensajeError.setPayload("Error al obtener los clientes: " + e.getMessage());

            Respuesta<String> respuestaError = new Respuesta<>();
            respuestaError.setEstado(Estado.ERROR);
            respuestaError.setMensaje(mensajeError);

            return respuestaError;
        }
    }

    @Override
    public Class<Object> getPayloadClass() {
        return Object.class;
    }

    private Metadata crearMetadata() {
        Metadata metadata = new Metadata();
        metadata.setIdMensaje(UUID.randomUUID().toString());
        metadata.setTimestamp(LocalDateTime.now());
        return metadata;
    }
}
