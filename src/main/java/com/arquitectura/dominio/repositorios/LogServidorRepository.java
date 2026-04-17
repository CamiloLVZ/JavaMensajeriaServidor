package com.arquitectura.dominio.repositorios;

import java.time.LocalDateTime;

public interface LogServidorRepository {

    void guardar(String nivel, String mensaje, String origen, String ipRemitente, LocalDateTime fechaEvento);
}
