package com.arquitectura.dominio.repositorios;

import com.arquitectura.dominio.modelo.LogServidorModel;
import com.arquitectura.infraestructura.persistencia.HibernateManager;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

import java.time.LocalDateTime;

public class JpaLogServidorRepository implements LogServidorRepository {

    @Override
    public void guardar(String nivel, String mensaje, String origen, String ipRemitente, LocalDateTime fechaEvento) {
        LogServidorModel entity = new LogServidorModel();
        entity.setNivel(nivel);
        entity.setMensaje(mensaje);
        entity.setOrigen(origen);
        entity.setIpRemitente(ipRemitente);
        entity.setFechaEvento(fechaEvento);

        EntityManager entityManager = HibernateManager.crearEntityManager();
        EntityTransaction transaction = entityManager.getTransaction();

        try {
            transaction.begin();
            entityManager.persist(entity);
            transaction.commit();
        } catch (Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw new IllegalStateException("No fue posible guardar el log del servidor en MySQL", e);
        } finally {
            entityManager.close();
        }
    }
}
