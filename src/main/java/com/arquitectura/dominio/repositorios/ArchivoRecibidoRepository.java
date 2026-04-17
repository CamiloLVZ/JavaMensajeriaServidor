package com.arquitectura.dominio.repositorios;

import java.time.LocalDateTime;

public interface ArchivoRecibidoRepository {

    void guardar(String mensajeId, String remitente, String ipRemitente, String nombreArchivo, String extension,
                 String rutaArchivo, String hashSha256, String contenidoCifrado,
                 long tamano, LocalDateTime fechaRecepcion);
}
