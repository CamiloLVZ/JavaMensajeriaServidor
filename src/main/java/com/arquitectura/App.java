package com.arquitectura;

import com.arquitectura.transporte.ProtocoloTransporte;
import com.arquitectura.transporte.ProtocoloTransporteFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class App
{
    public static void main(String[] args) {
        Properties properties = new Properties();

        try (InputStream inputStream = App.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (inputStream == null) {
                throw new IllegalStateException("No se encontro application.properties en el classpath.");
            }

            properties.load(inputStream);
            System.out.println("Protocolo: " + properties.getProperty("transfer-protocol"));
            System.out.println("Puerto: " + properties.getProperty("server.port"));
            System.out.println("Clientes Maximos: " + properties.getProperty("max-clients"));

            ProtocoloTransporte protocoloTransporte = ProtocoloTransporteFactory.crear(properties.getProperty("transfer-protocol"));


        } catch (IOException e) {
            throw new RuntimeException("No fue posible leer application.properties.", e);
        }

    }
}
