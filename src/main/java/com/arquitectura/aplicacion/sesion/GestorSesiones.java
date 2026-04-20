package com.arquitectura.aplicacion.sesion;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor singleton de sesiones en memoria.
 *
 * <p>Controla:
 * - usernames únicos,
 * - límite máximo de sesiones,
 * - expiración por inactividad.</p>
 */
public class GestorSesiones {

    private static final GestorSesiones INSTANCE = new GestorSesiones();

    private final Map<String, SesionCliente> sesionesPorUsername = new ConcurrentHashMap<>();

    private volatile int maxSesiones = 10;
    private volatile Duration timeoutInactividad = Duration.ofMinutes(30);

    private GestorSesiones() {
    }

    public static GestorSesiones getInstance() {
        return INSTANCE;
    }

    public void configurar(int maxSesiones, Duration timeoutInactividad) {
        this.maxSesiones = maxSesiones;
        if (timeoutInactividad != null && !timeoutInactividad.isNegative() && !timeoutInactividad.isZero()) {
            this.timeoutInactividad = timeoutInactividad;
        }
    }

    /**
     * Registra una sesión nueva si el username está libre y hay cupo.
     */
    public synchronized ResultadoRegistroSesion registrar(String username, String endpoint, String protocolo) {
        limpiarExpiradas();

        String usernameNormalizado = normalizar(username);
        if (usernameNormalizado.isBlank()) {
            return ResultadoRegistroSesion.error("USERNAME_INVALIDO", "El username es obligatorio");
        }

        SesionCliente existente = sesionesPorUsername.get(usernameNormalizado);
        if (existente != null) {
            // Si el mismo usuario reintenta desde el mismo endpoint/protocolo, aceptamos idempotente.
            if (existente.getEndpoint().equals(endpoint) && existente.getProtocolo().equalsIgnoreCase(protocolo)) {
                existente.marcarActividad();
                return ResultadoRegistroSesion.ok(existente, "Sesion ya existente para el usuario");
            }
            return ResultadoRegistroSesion.error("USERNAME_YA_REGISTRADO", "El username ya está en uso");
        }

        if (sesionesPorUsername.size() >= maxSesiones) {
            return ResultadoRegistroSesion.error("MAX_SESIONES_ALCANZADO", "No hay cupo para nuevas sesiones");
        }

        SesionCliente sesion = new SesionCliente(usernameNormalizado, endpoint, protocolo);
        sesionesPorUsername.put(usernameNormalizado, sesion);
        return ResultadoRegistroSesion.ok(sesion, "Sesion registrada correctamente");
    }

    public boolean existeSesionActiva(String username) {
        limpiarExpiradas();
        if (username == null || username.isBlank()) {
            return false;
        }
        SesionCliente sesion = sesionesPorUsername.get(normalizar(username));
        return sesion != null;
    }

    public Optional<SesionCliente> obtener(String username) {
        limpiarExpiradas();
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(sesionesPorUsername.get(normalizar(username)));
    }

    public void marcarActividad(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        SesionCliente sesion = sesionesPorUsername.get(normalizar(username));
        if (sesion != null) {
            sesion.marcarActividad();
        }
    }

    public synchronized void limpiarExpiradas() {
        Instant ahora = Instant.now();
        sesionesPorUsername.entrySet().removeIf(entry ->
                Duration.between(entry.getValue().getUltimoAcceso(), ahora).compareTo(timeoutInactividad) > 0
        );
    }

    public int sesionesActivas() {
        limpiarExpiradas();
        return sesionesPorUsername.size();
    }

    private String normalizar(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
