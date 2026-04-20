package com.arquitectura.aplicacion;

import com.arquitectura.comun.dto.PaqueteDatos;
import com.arquitectura.infraestructura.serializacion.JsonUtil;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.aplicacion.router.MensajeRouter;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadConectar;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public class ProcesadorMensajes {

    private final MensajeRouter router;

    public ProcesadorMensajes(MensajeRouter router) {
        this.router = router;
    }

    public String procesar(PaqueteDatos paqueteDatos) {
        ContextoSolicitud.establecerOrigen(
                resolverIpRemitente(paqueteDatos),
                resolverPuertoRemitente(paqueteDatos),
                resolverProtocolo(paqueteDatos)
        );

        try {
            return procesarInterno(paqueteDatos.getData());
        } finally {
            ContextoSolicitud.limpiar();
        }
    }

    private String procesarInterno(byte[] data) {
        String json = new String(data, StandardCharsets.UTF_8);

        Mensaje<?> mensaje = resolverMensaje(json);

        Respuesta<?> respuesta = router.responder(mensaje);

        return JsonUtil.toJson(respuesta);
    }

    private Mensaje<?> resolverMensaje(String json) {
        Map<String, Object> data = JsonUtil.fromJson(json, Map.class);
        Object type = data.get("type");

        if (type != null && "REGISTER".equalsIgnoreCase(String.valueOf(type))) {
            return convertirRegistroPlano(data);
        }

        return JsonUtil.fromJson(json, Mensaje.class);
    }

    /**
     * Soporte para entrada simplificada:
     * { "type": "REGISTER", "username": "juan" }
     */
    private Mensaje<PayloadConectar> convertirRegistroPlano(Map<String, Object> data) {
        PayloadConectar payload = new PayloadConectar();
        Object username = data.get("username");
        payload.setUsername(username == null ? "" : String.valueOf(username));

        Metadata metadata = new Metadata();
        metadata.setIdMensaje(UUID.randomUUID().toString());
        metadata.setTimestamp(LocalDateTime.now());

        Mensaje<PayloadConectar> mensaje = new Mensaje<>();
        mensaje.setTipo(TipoMensaje.REQUEST);
        mensaje.setAccion(Accion.CONECTAR);
        mensaje.setMetadata(metadata);
        mensaje.setPayload(payload);
        return mensaje;
    }

    private String resolverIpRemitente(PaqueteDatos paqueteDatos) {
        if (paqueteDatos.getSocket() != null && paqueteDatos.getSocket().getInetAddress() != null) {
            return paqueteDatos.getSocket().getInetAddress().getHostAddress();
        }

        return paqueteDatos.getHostOrigen();
    }

    private int resolverPuertoRemitente(PaqueteDatos paqueteDatos) {
        if (paqueteDatos.getSocket() != null) {
            return paqueteDatos.getSocket().getPort();
        }
        return paqueteDatos.getPuertoOrigen();
    }

    private String resolverProtocolo(PaqueteDatos paqueteDatos) {
        return paqueteDatos.getSocket() != null ? "TCP" : "UDP";
    }
}
