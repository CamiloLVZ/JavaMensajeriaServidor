package com.arquitectura.aplicacion.router;

import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.dominio.handlers.ConectarHandler;

public class MensajeRouterFactory {

    public static MensajeRouter crearRouter() {

        MensajeRouter router = new MensajeRouter();
        router.registrarHandler(Accion.CONECTAR, new ConectarHandler());

        return router;
    }
}