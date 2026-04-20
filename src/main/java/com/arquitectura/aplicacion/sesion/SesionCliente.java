package com.arquitectura.aplicacion.sesion;

import java.time.Instant;

/**
 * Representa una sesión activa en memoria.
 */
public class SesionCliente {

    private final String username;
    private final String endpoint;
    private final String protocolo;
    private final Instant creadoEn;
    private volatile Instant ultimoAcceso;

    public SesionCliente(String username, String endpoint, String protocolo) {
        this.username = username;
        this.endpoint = endpoint;
        this.protocolo = protocolo;
        this.creadoEn = Instant.now();
        this.ultimoAcceso = this.creadoEn;
    }

    public String getUsername() {
        return username;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getProtocolo() {
        return protocolo;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getUltimoAcceso() {
        return ultimoAcceso;
    }

    public void marcarActividad() {
        this.ultimoAcceso = Instant.now();
    }
}
