package com.arquitectura.comun.dto;

import java.io.*;
import java.util.Arrays;

/**
 * Frame binario para transferencia de chunks de archivos grandes.
 * Formato:
 * - transferId (36 bytes, UUID string)
 * - indexChunk (8 bytes, long)
 * - totalChunks (8 bytes, long)
 * - bytesChunk (4 bytes, int)
 * - chunkData (variable)
 * - chunkSha256 (32 bytes)
 */
public class FrameTransferencia implements Serializable {

    private String transferId;
    private long indexChunk;
    private long totalChunks;
    private byte[] chunkData;
    private byte[] chunkSha256; // 32 bytes
    private long tamanoTotal;

    public FrameTransferencia() {
    }

    public FrameTransferencia(String transferId, long indexChunk, long totalChunks,
                            byte[] chunkData, byte[] chunkSha256, long tamanoTotal) {
        this.transferId = transferId;
        this.indexChunk = indexChunk;
        this.totalChunks = totalChunks;
        this.chunkData = chunkData;
        this.chunkSha256 = chunkSha256;
        this.tamanoTotal = tamanoTotal;
    }

    public byte[] serializar() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // Escribir transferId (máx 36 bytes, UUID)
        byte[] idBytes = transferId.getBytes("UTF-8");
        dos.writeShort(idBytes.length);
        dos.write(idBytes);

        // Escribir índices y control
        dos.writeLong(indexChunk);
        dos.writeLong(totalChunks);
        dos.writeLong(tamanoTotal);

        // Escribir tamaño del chunk
        dos.writeInt(chunkData != null ? chunkData.length : 0);

        // Escribir datos del chunk
        if (chunkData != null && chunkData.length > 0) {
            dos.write(chunkData);
        }

        // Escribir hash SHA-256 (32 bytes)
        if (chunkSha256 != null && chunkSha256.length == 32) {
            dos.write(chunkSha256);
        } else {
            dos.write(new byte[32]); // 32 bytes en blanco si no hay hash
        }

        dos.flush();
        return baos.toByteArray();
    }

    public static FrameTransferencia deserializar(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);

        // Leer transferId
        short idLength = dis.readShort();
        byte[] idBytes = new byte[idLength];
        dis.readFully(idBytes);
        String transferId = new String(idBytes, "UTF-8");

        // Leer índices y control
        long indexChunk = dis.readLong();
        long totalChunks = dis.readLong();
        long tamanoTotal = dis.readLong();

        // Leer tamaño del chunk
        int chunkSize = dis.readInt();

        // Leer datos del chunk
        byte[] chunkData = null;
        if (chunkSize > 0) {
            chunkData = new byte[chunkSize];
            dis.readFully(chunkData);
        }

        // Leer hash
        byte[] chunkSha256 = new byte[32];
        dis.readFully(chunkSha256);

        return new FrameTransferencia(transferId, indexChunk, totalChunks, chunkData, chunkSha256, tamanoTotal);
    }

    // Getters y Setters
    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    public long getIndexChunk() {
        return indexChunk;
    }

    public void setIndexChunk(long indexChunk) {
        this.indexChunk = indexChunk;
    }

    public long getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(long totalChunks) {
        this.totalChunks = totalChunks;
    }

    public byte[] getChunkData() {
        return chunkData;
    }

    public void setChunkData(byte[] chunkData) {
        this.chunkData = chunkData;
    }

    public byte[] getChunkSha256() {
        return chunkSha256;
    }

    public void setChunkSha256(byte[] chunkSha256) {
        this.chunkSha256 = chunkSha256;
    }

    public long getTamanoTotal() {
        return tamanoTotal;
    }

    public void setTamanoTotal(long tamanoTotal) {
        this.tamanoTotal = tamanoTotal;
    }
}

