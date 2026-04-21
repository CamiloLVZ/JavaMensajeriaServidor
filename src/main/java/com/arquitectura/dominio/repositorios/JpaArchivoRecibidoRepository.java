package com.arquitectura.dominio.repositorios;

import com.arquitectura.dominio.modelo.ArchivoRecibidoModel;
import com.arquitectura.infraestructura.persistencia.HibernateManager;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

import java.time.LocalDateTime;
import java.util.List;

public class JpaArchivoRecibidoRepository implements ArchivoRecibidoRepository {

    @Override
    public void guardar(String mensajeId, String remitente, String ipRemitente, String nombreArchivo, String extension,
                        String rutaArchivo, String hashSha256, String contenidoCifrado,
                        long tamano, LocalDateTime fechaRecepcion) {

        ArchivoRecibidoModel entity = new ArchivoRecibidoModel();
        entity.setId(mensajeId);
        entity.setRemitente(remitente);
        entity.setIpRemitente(ipRemitente);
        entity.setNombreArchivo(nombreArchivo);
        entity.setExtension(extension);
        entity.setRutaArchivo(rutaArchivo);
        entity.setHashSha256(hashSha256);
        entity.setContenidoCifrado(contenidoCifrado);
        entity.setTamano(tamano);
        entity.setFechaRecepcion(fechaRecepcion);

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
            throw new IllegalStateException("No fue posible guardar la ruta del archivo en MySQL", e);
        } finally {
            entityManager.close();
        }
    }

    @Override
    public List<ArchivoRecibidoModel> listarTodos() {
        EntityManager em = HibernateManager.crearEntityManager();
        try {
            return em.createQuery(
                "SELECT a FROM ArchivoRecibidoModel a ORDER BY a.fechaRecepcion DESC",
                ArchivoRecibidoModel.class
            ).getResultList();
        } finally {
            em.close();
        }
    }
}
