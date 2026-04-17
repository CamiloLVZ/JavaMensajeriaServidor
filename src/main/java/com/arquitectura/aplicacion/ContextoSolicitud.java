package com.arquitectura.aplicacion;

public final class ContextoSolicitud {

    private static final ThreadLocal<String> IP_REMITENTE = new ThreadLocal<>();

    private ContextoSolicitud() {
    }

    public static void establecerIpRemitente(String ipRemitente) {
        IP_REMITENTE.set(ipRemitente);
    }

    public static String obtenerIpRemitente() {
        return IP_REMITENTE.get();
    }

    public static void limpiar() {
        IP_REMITENTE.remove();
    }
}
