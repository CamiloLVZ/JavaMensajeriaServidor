package com.arquitectura.dominio.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "transferencias_streaming")
public class TransferenciaStreamingModel {

    @Id
    @Column(nullable = false, length = 36, updatable = false)
    private String transferId;

    @Column(nullable = false, length = 255)
    private String nombreArchivo;

    @Column(nullable = false)
    private long tamanoTotal;

    @Column(nullable = false)
    private long offsetActual;

    @Column(nullable = false, length = 50)
    private String estado; // IN_PROGRESS, COMPLETED, FAILED, CANCELLED

    @Column(name = "chunks_recibidos", nullable = false)
    private long chunksRecibidos;

    @Column(name = "total_chunks", nullable = false)
    private long totalChunks;

    @Column(nullable = false, length = 255)
    private String remitente;

    @Column(name = "ip_remitente", length = 45)
    private String ipRemitente;

    @Column(name = "ruta_temporal", columnDefinition = "TEXT")
    private String rutaTemporal;

    @Column(name = "hash_final", length = 88)
    private String hashFinal;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_ultima_actividad", nullable = false)
    private LocalDateTime fechaUltimaActividad;

    @Column(name = "fecha_finalizacion")
    private LocalDateTime fechaFinalizacion;

    @Column(name = "protocolo", length = 10)
    private String protocolo; // TCP, UDP

    @Column(name = "hash_incremental", columnDefinition = "LONGBLOB")
    private byte[] hashIncremental; // Estado actual del MessageDigest

    // Constructores
    public TransferenciaStreamingModel() {
    }

    public TransferenciaStreamingModel(String transferId, String nombreArchivo, long tamanoTotal,
                                      String remitente, String ipRemitente, long totalChunks, String protocolo) {
        this.transferId = transferId;
        this.nombreArchivo = nombreArchivo;
        this.tamanoTotal = tamanoTotal;
        this.offsetActual = 0;
        this.estado = "IN_PROGRESS";
        this.chunksRecibidos = 0;
        this.totalChunks = totalChunks;
        this.remitente = remitente;
        this.ipRemitente = ipRemitente;
        this.protocolo = protocolo;
        this.fechaInicio = LocalDateTime.now();
        this.fechaUltimaActividad = LocalDateTime.now();
    }

    // Getters y Setters
    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public void setNombreArchivo(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }

    public long getTamanoTotal() {
        return tamanoTotal;
    }

    public void setTamanoTotal(long tamanoTotal) {
        this.tamanoTotal = tamanoTotal;
    }

    public long getOffsetActual() {
        return offsetActual;
    }

    public void setOffsetActual(long offsetActual) {
        this.offsetActual = offsetActual;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public long getChunksRecibidos() {
        return chunksRecibidos;
    }

    public void setChunksRecibidos(long chunksRecibidos) {
        this.chunksRecibidos = chunksRecibidos;
    }

    public long getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(long totalChunks) {
        this.totalChunks = totalChunks;
    }

    public String getRemitente() {
        return remitente;
    }

    public void setRemitente(String remitente) {
        this.remitente = remitente;
    }

    public String getIpRemitente() {
        return ipRemitente;
    }

    public void setIpRemitente(String ipRemitente) {
        this.ipRemitente = ipRemitente;
    }

    public String getRutaTemporal() {
        return rutaTemporal;
    }

    public void setRutaTemporal(String rutaTemporal) {
        this.rutaTemporal = rutaTemporal;
    }

    public String getHashFinal() {
        return hashFinal;
    }

    public void setHashFinal(String hashFinal) {
        this.hashFinal = hashFinal;
    }

    public LocalDateTime getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(LocalDateTime fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public LocalDateTime getFechaUltimaActividad() {
        return fechaUltimaActividad;
    }

    public void setFechaUltimaActividad(LocalDateTime fechaUltimaActividad) {
        this.fechaUltimaActividad = fechaUltimaActividad;
    }

    public LocalDateTime getFechaFinalizacion() {
        return fechaFinalizacion;
    }

    public void setFechaFinalizacion(LocalDateTime fechaFinalizacion) {
        this.fechaFinalizacion = fechaFinalizacion;
    }

    public String getProtocolo() {
        return protocolo;
    }

    public void setProtocolo(String protocolo) {
        this.protocolo = protocolo;
    }

    public byte[] getHashIncremental() {
        return hashIncremental;
    }

    public void setHashIncremental(byte[] hashIncremental) {
        this.hashIncremental = hashIncremental;
    }

    @Override
    public String toString() {
        return "TransferenciaStreamingModel{" +
                "transferId='" + transferId + '\'' +
                ", nombreArchivo='" + nombreArchivo + '\'' +
                ", estado='" + estado + '\'' +
                ", offsetActual=" + offsetActual +
                ", tamanoTotal=" + tamanoTotal +
                '}';
    }
}

