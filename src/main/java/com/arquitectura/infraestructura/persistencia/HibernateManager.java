package com.arquitectura.infraestructura.persistencia;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.Query;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public final class HibernateManager {

    private static EntityManagerFactory entityManagerFactory;

    private HibernateManager() {
    }

    public static void inicializar(Properties properties) {
        if (entityManagerFactory != null) {
            return;
        }

        Map<String, Object> config = new HashMap<>();
        config.put("jakarta.persistence.jdbc.url", obtenerRequerida(properties, "mysql.url"));
        config.put("jakarta.persistence.jdbc.user", obtenerRequerida(properties, "mysql.user"));
        config.put("jakarta.persistence.jdbc.password", obtenerRequerida(properties, "mysql.password"));
        config.put("jakarta.persistence.jdbc.driver", "com.mysql.cj.jdbc.Driver");
        config.put("hibernate.hbm2ddl.auto", properties.getProperty("hibernate.hbm2ddl.auto", "update"));
        config.put("hibernate.show_sql", properties.getProperty("hibernate.show_sql", "false"));
        config.put("hibernate.format_sql", properties.getProperty("hibernate.format_sql", "false"));
        config.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");

        try {
            entityManagerFactory = Persistence.createEntityManagerFactory("mensajeriaPU", config);
            validarConexion();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No fue posible inicializar Hibernate/MySQL. Verifica mysql.url, mysql.user, mysql.password y que MySQL este disponible.",
                    e
            );
        }
    }

    public static EntityManager crearEntityManager() {
        if (entityManagerFactory == null) {
            throw new IllegalStateException("Hibernate no ha sido inicializado");
        }

        return entityManagerFactory.createEntityManager();
    }

    public static void cerrar() {
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
        entityManagerFactory = null;
    }

    private static String obtenerRequerida(Properties properties, String key) {
        String valor = properties.getProperty(key);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Falta la propiedad requerida: " + key);
        }

        return valor;
    }
    private static void validarConexion() {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try {
            Query query = entityManager.createNativeQuery("SELECT 1");
            query.getSingleResult();
        } finally {
            entityManager.close();
        }
    }

}
