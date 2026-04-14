package com.arquitectura;

import com.arquitectura.transporte.PaqueteDatos;
import com.arquitectura.transporte.ProtocoloTransporte;
import com.arquitectura.transporte.ProtocoloTransporteFactory;

import java.io.InputStream;
import java.util.Properties;

public class App {

    public static void main(String[] args) {

        Properties properties = new Properties();

        try (InputStream inputStream = App.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (inputStream == null) {
                throw new IllegalStateException("No se encontró application.properties en el classpath.");
            }

            properties.load(inputStream);

            String protocolo = properties.getProperty("transfer-protocol");
            int puerto = Integer.parseInt(properties.getProperty("server.port"));

            System.out.println("Protocolo: " + protocolo);
            System.out.println("Puerto: " + puerto);

            ProtocoloTransporte transporte = ProtocoloTransporteFactory.crear(protocolo);

            // 🔹 Iniciar "servidor"
            transporte.iniciar(puerto);

            // 🔹 Recibir mensaje (bloqueante)
            System.out.println("[SERVIDOR] Esperando mensaje...");
            PaqueteDatos paquete = transporte.recibir();

            String mensajeRecibido = new String(paquete.getData());

            System.out.println("[SERVIDOR] Mensaje recibido: " + mensajeRecibido);
            System.out.println("[SERVIDOR] Desde: " + paquete.getHostOrigen() + ":" + paquete.getPuertoOrigen());

            transporte.detener();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
