package com.arquitectura.transporte;

import java.util.Arrays;

public class PaqueteDatos {

    private final byte[] data;
    private final String hostOrigen;
    private final int puertoOrigen;

    public PaqueteDatos(byte[] data, String hostOrigen, int puertoOrigen) {
        this.data = Arrays.copyOf(data, data.length);
        this.hostOrigen = hostOrigen;
        this.puertoOrigen = puertoOrigen;
    }

    public byte[] getData() {
        return Arrays.copyOf(data, data.length);
    }

    public String getHostOrigen() {
        return hostOrigen;
    }

    public int getPuertoOrigen() {
        return puertoOrigen;
    }
}
