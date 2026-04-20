package com.arquitectura.infraestructura.concurrencia;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Supplier;

/**
 * Pool de objetos reutilizables (Object Pool).
 *
 * <p>Su responsabilidad es mantener una cantidad fija de instancias pre-creadas
 * para evitar crear/destruir objetos en cada solicitud.</p>
 *
 * @param <T> tipo de objeto a reutilizar.
 */
public class ObjectPool<T> {

    private final BlockingQueue<T> disponibles;

    /**
     * Crea el pool y llena todas sus posiciones con objetos listos para usar.
     *
     * @param tamanoMaximo cantidad máxima de objetos disponibles simultáneamente.
     * @param creador      función que crea cada objeto inicial del pool.
     */
    public ObjectPool(int tamanoMaximo, Supplier<T> creador) {
        if (tamanoMaximo <= 0) {
            throw new IllegalArgumentException("El tamaño del pool debe ser mayor a 0");
        }

        this.disponibles = new ArrayBlockingQueue<>(tamanoMaximo);

        for (int i = 0; i < tamanoMaximo; i++) {
            disponibles.add(creador.get());
        }
    }

    /**
     * Toma (presta) un objeto del pool.
     *
     * <p>Si no hay objetos disponibles, espera hasta que otro hilo lo libere.
     * Esto permite limitar de forma natural la concurrencia máxima.</p>
     */
    public T tomar() throws InterruptedException {
        return disponibles.take();
    }

    /**
     * Devuelve un objeto al pool para que pueda reutilizarse.
     */
    public void devolver(T objeto) {
        if (objeto == null) {
            return;
        }
        disponibles.offer(objeto);
    }
}
