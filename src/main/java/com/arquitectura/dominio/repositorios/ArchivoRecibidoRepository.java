package com.arquitectura.dominio.repositorios;

import com.arquitectura.dominio.modelo.ArchivoRecibidoModel;

import java.time.LocalDateTime;
import java.util.List;

public interface ArchivoRecibidoRepository {

    void guardar(String mensajeId, String remitente, String ipRemitente, String nombreArchivo, String extension,
                 String rutaArchivo, String hashSha256, String contenidoCifrado,
                 long tamano, LocalDateTime fechaRecepcion);

    List<ArchivoRecibidoModel> listarTodos();
}
