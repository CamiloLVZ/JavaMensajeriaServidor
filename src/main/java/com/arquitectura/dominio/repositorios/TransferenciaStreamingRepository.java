package com.arquitectura.dominio.repositorios;

import com.arquitectura.dominio.modelo.TransferenciaStreamingModel;

import java.util.Optional;

public interface TransferenciaStreamingRepository {
    void guardar(TransferenciaStreamingModel transferencia);
    Optional<TransferenciaStreamingModel> obtenerPorId(String transferId);
    void actualizar(TransferenciaStreamingModel transferencia);
    void eliminar(String transferId);
}