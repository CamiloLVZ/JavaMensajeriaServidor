package com.arquitectura.aplicacion;

import com.arquitectura.comun.dto.PaqueteDatos;
import com.arquitectura.infraestructura.serializacion.JsonUtil;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.aplicacion.router.MensajeRouter;

import java.nio.charset.StandardCharsets;

public class ProcesadorMensajes {

    private final MensajeRouter router;

    public ProcesadorMensajes(MensajeRouter router) {
        this.router = router;
    }

    public String procesar(PaqueteDatos paqueteDatos) {
        ContextoSolicitud.establecerIpRemitente(resolverIpRemitente(paqueteDatos));

        try {
            return procesarInterno(paqueteDatos.getData());
        } finally {
            ContextoSolicitud.limpiar();
        }
    }

    private String procesarInterno(byte[] data) {
        String json = new String(data, StandardCharsets.UTF_8);

        Mensaje<?> mensaje = JsonUtil.fromJson(json, Mensaje.class);

        Respuesta<?> respuesta = router.responder(mensaje);

        return JsonUtil.toJson(respuesta);
    }

    private String resolverIpRemitente(PaqueteDatos paqueteDatos) {
        if (paqueteDatos.getSocket() != null && paqueteDatos.getSocket().getInetAddress() != null) {
            return paqueteDatos.getSocket().getInetAddress().getHostAddress();
        }

        return paqueteDatos.getHostOrigen();
    }
}
