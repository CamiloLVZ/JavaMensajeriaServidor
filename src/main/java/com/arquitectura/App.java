package com.arquitectura;

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
            System.out.println(properties.getProperty("transfer-protocol"));
        } catch (IOException e) {
            throw new RuntimeException("No fue posible leer application.properties.", e);
        }
    }
}
