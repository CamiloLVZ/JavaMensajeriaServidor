package com.arquitectura.router;

import com.arquitectura.mensajeria.*;

public interface Handler<T> {
    Respuesta<?> handle(Mensaje<T> mensaje);
    Class<T> getPayloadClass();
}