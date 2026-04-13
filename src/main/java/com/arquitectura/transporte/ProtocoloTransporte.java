package com.arquitectura.transporte;

public interface ProtocoloTransporte {

    void iniciar(int port);
    void enviar(byte[] data, String host, int port);
    PaqueteDatos recibir();
    void detener();
}