package com.arquitectura.aplicacion.router;

import com.arquitectura.dominio.handlers.ConectarHandler;
import com.arquitectura.dominio.handlers.DesconectarHandler;
import com.arquitectura.dominio.handlers.EnviarArchivoHandler;
import com.arquitectura.dominio.handlers.FinalizarStreamHandler;
import com.arquitectura.dominio.handlers.IniciarStreamHandler;
import com.arquitectura.dominio.handlers.ListarClientesHandler;
import com.arquitectura.dominio.handlers.ListarDocumentosHandler;
import com.arquitectura.dominio.handlers.ListarLogsHandler;
import com.arquitectura.dominio.handlers.ListarMensajesHandler;
import com.arquitectura.dominio.handlers.MensajeTextoHandler;
import com.arquitectura.dominio.handlers.ObtenerArchivoHandler;
import com.arquitectura.mensajeria.enums.Accion;

public class MensajeRouterFactory {

    public static MensajeRouter crearRouter() {

        MensajeRouter router = new MensajeRouter();
        router.registrarHandler(Accion.CONECTAR,          new ConectarHandler());
        router.registrarHandler(Accion.DESCONECTAR,        new DesconectarHandler());
        router.registrarHandler(Accion.ENVIAR_DOCUMENTO,  new EnviarArchivoHandler());
        router.registrarHandler(Accion.ENVIAR_MENSAJE,    new MensajeTextoHandler());
        router.registrarHandler(Accion.LISTAR_MENSAJES,   new ListarMensajesHandler());
        router.registrarHandler(Accion.LISTAR_DOCUMENTOS, new ListarDocumentosHandler());
        router.registrarHandler(Accion.LISTAR_LOGS,       new ListarLogsHandler());
        router.registrarHandler(Accion.LISTAR_CLIENTES,   new ListarClientesHandler());
        router.registrarHandler(Accion.INICIAR_STREAM,    new IniciarStreamHandler());
        router.registrarHandler(Accion.FINALIZAR_STREAM,  new FinalizarStreamHandler());
        router.registrarHandler(Accion.SOLICITAR_STREAM,  new ObtenerArchivoHandler());

        return router;
    }
}
