package com.arquitectura.aplicacion.sesion;

/**
 * Resultado de intentar registrar una sesión.
 */
public record ResultadoRegistroSesion(boolean exito, String codigoError, String mensaje, SesionCliente sesion) {

    public static ResultadoRegistroSesion ok(SesionCliente sesion, String mensaje) {
        return new ResultadoRegistroSesion(true, null, mensaje, sesion);
    }

    public static ResultadoRegistroSesion error(String codigoError, String mensaje) {
        return new ResultadoRegistroSesion(false, codigoError, mensaje, null);
    }
}
