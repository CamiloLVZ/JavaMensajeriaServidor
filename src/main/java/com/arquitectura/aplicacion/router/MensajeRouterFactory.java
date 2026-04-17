package com.arquitectura.aplicacion.router;

import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.dominio.handlers.ConectarHandler;
import com.arquitectura.dominio.handlers.EnviarArchivoHandler;
import com.arquitectura.dominio.handlers.MensajeTextoHandler;

public class MensajeRouterFactory {

    public static MensajeRouter crearRouter() {

        MensajeRouter router = new MensajeRouter();
        router.registrarHandler(Accion.CONECTAR, new ConectarHandler());
        router.registrarHandler(Accion.ENVIAR_DOCUMENTO, new EnviarArchivoHandler());
        router.registrarHandler(Accion.ENVIAR_MENSAJE, new MensajeTextoHandler());

        return router;
    }
}
