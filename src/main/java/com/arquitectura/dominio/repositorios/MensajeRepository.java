package com.arquitectura.dominio.repositorios;

import java.time.LocalDateTime;

public interface MensajeRepository {

    void guardar(String mensajeId, String autor, String ipRemitente, String contenido,
                 String hashSha256, String contenidoCifrado, LocalDateTime fechaEnvio);
}
