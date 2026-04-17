package com.arquitectura.dominio.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "mensajes")
public class MensajeModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mensaje_id", nullable = false, length = 100)
    private String mensajeId;

    @Column(nullable = false, length = 255)
    private String autor;

    @Column(name = "ip_remitente", length = 45)
    private String ipRemitente;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenido;

    @Column(name = "hash_sha256", nullable = false, length = 88)
    private String hashSha256;

    @Column(name = "contenido_cifrado", nullable = false, columnDefinition = "LONGTEXT")
    private String contenidoCifrado;

    @Column(name = "fecha_envio", nullable = false)
    private LocalDateTime fechaEnvio;

    public Long getId() {
        return id;
    }

    public String getMensajeId() {
        return mensajeId;
    }

    public void setMensajeId(String mensajeId) {
        this.mensajeId = mensajeId;
    }

    public String getAutor() {
        return autor;
    }

    public void setAutor(String autor) {
        this.autor = autor;
    }

    public String getIpRemitente() {
        return ipRemitente;
    }

    public void setIpRemitente(String ipRemitente) {
        this.ipRemitente = ipRemitente;
    }

    public String getContenido() {
        return contenido;
    }

    public void setContenido(String contenido) {
        this.contenido = contenido;
    }

    public String getHashSha256() {
        return hashSha256;
    }

    public void setHashSha256(String hashSha256) {
        this.hashSha256 = hashSha256;
    }

    public String getContenidoCifrado() {
        return contenidoCifrado;
    }

    public void setContenidoCifrado(String contenidoCifrado) {
        this.contenidoCifrado = contenidoCifrado;
    }

    public LocalDateTime getFechaEnvio() {
        return fechaEnvio;
    }

    public void setFechaEnvio(LocalDateTime fechaEnvio) {
        this.fechaEnvio = fechaEnvio;
    }
}
