package com.arquitectura.dominio.repositorios;

import com.arquitectura.dominio.modelo.TransferenciaStreamingModel;
import com.arquitectura.infraestructura.persistencia.HibernateManager;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;

import java.util.Optional;

public class JpaTransferenciaStreamingRepository implements TransferenciaStreamingRepository {

    @Override
    public void guardar(TransferenciaStreamingModel transferencia) {
        EntityManager em = HibernateManager.crearEntityManager();
        try {
            em.getTransaction().begin();
            em.persist(transferencia);
            em.getTransaction().commit();
        } catch (Exception e) {
            em.getTransaction().rollback();
            throw new RuntimeException("Error guardando transferencia streaming", e);
        } finally {
            em.close();
        }
    }

    @Override
    public Optional<TransferenciaStreamingModel> obtenerPorId(String transferId) {
        EntityManager em = HibernateManager.crearEntityManager();
        try {
            TransferenciaStreamingModel transferencia = em.find(TransferenciaStreamingModel.class, transferId);
            return Optional.ofNullable(transferencia);
        } catch (NoResultException e) {
            return Optional.empty();
        } finally {
            em.close();
        }
    }

    @Override
    public void actualizar(TransferenciaStreamingModel transferencia) {
        EntityManager em = HibernateManager.crearEntityManager();
        try {
            em.getTransaction().begin();
            em.merge(transferencia);
            em.getTransaction().commit();
        } catch (Exception e) {
            em.getTransaction().rollback();
            throw new RuntimeException("Error actualizando transferencia streaming", e);
        } finally {
            em.close();
        }
    }

    @Override
    public void eliminar(String transferId) {
        EntityManager em = HibernateManager.crearEntityManager();
        try {
            em.getTransaction().begin();
            TransferenciaStreamingModel transferencia = em.find(TransferenciaStreamingModel.class, transferId);
            if (transferencia != null) {
                em.remove(transferencia);
            }
            em.getTransaction().commit();
        } catch (Exception e) {
            em.getTransaction().rollback();
            throw new RuntimeException("Error eliminando transferencia streaming", e);
        } finally {
            em.close();
        }
    }
}



