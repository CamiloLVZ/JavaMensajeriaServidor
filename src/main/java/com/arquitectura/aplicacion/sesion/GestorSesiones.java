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
 * - usernames unicos,
 * - limite maximo de sesiones,
 * - expiracion por inactividad,
 * - validacion del origen que puede operar una sesion.</p>
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
     * Registra una sesion nueva si el username esta libre y hay cupo.
     */
    public synchronized ResultadoRegistroSesion registrar(String username, String endpoint, String protocolo) {
        return registrar(username, extraerIp(endpoint), extraerPuerto(endpoint), protocolo);
    }

    public synchronized ResultadoRegistroSesion registrar(String username, String ipRemitente, int puertoRemitente, String protocolo) {
        limpiarExpiradas();

        String usernameNormalizado = normalizar(username);
        if (usernameNormalizado.isBlank()) {
            return ResultadoRegistroSesion.error("USERNAME_INVALIDO", "El username es obligatorio");
        }

        SesionCliente existente = sesionesPorUsername.get(usernameNormalizado);
        if (existente != null) {
            if (existente.mismaConexion(ipRemitente, puertoRemitente, protocolo)) {
                existente.marcarActividad();
                return ResultadoRegistroSesion.ok(existente, "Sesion ya existente para el usuario");
            }

            // Para reconexiones del mismo cliente aceptamos el mismo usuario si mantiene IP y protocolo.
            if (existente.mismoCanalLogico(ipRemitente, protocolo)) {
                existente.actualizarOrigen(ipRemitente, puertoRemitente, protocolo);
                return ResultadoRegistroSesion.reconexion(existente, "Sesion actualizada para nueva conexion del mismo cliente");
            }

            return ResultadoRegistroSesion.error("USERNAME_YA_REGISTRADO", "El username ya esta en uso");
        }

        if (sesionesPorUsername.size() >= maxSesiones) {
            return ResultadoRegistroSesion.error("MAX_SESIONES_ALCANZADO", "No hay cupo para nuevas sesiones");
        }

        SesionCliente sesion = new SesionCliente(usernameNormalizado, ipRemitente, puertoRemitente, protocolo);
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

    public ResultadoValidacionSesion validarSesion(String username, String ipRemitente, int puertoRemitente, String protocolo) {
        limpiarExpiradas();
        if (username == null || username.isBlank()) {
            return ResultadoValidacionSesion.error("SESION_NO_REGISTRADA", "El usuario no fue informado");
        }

        SesionCliente sesion = sesionesPorUsername.get(normalizar(username));
        if (sesion == null) {
            return ResultadoValidacionSesion.error(
                    "SESION_NO_REGISTRADA",
                    "El usuario [" + username + "] no tiene una sesion activa. Primero debe registrarse."
            );
        }

        if (!sesion.aceptaOperacionDesde(ipRemitente, puertoRemitente, protocolo)) {
            return ResultadoValidacionSesion.error(
                    "ORIGEN_SESION_INVALIDO",
                    "La sesion activa de [" + sesion.getUsername() + "] no corresponde al origen actual "
                            + describirOrigen(ipRemitente, puertoRemitente, protocolo)
            );
        }

        sesion.marcarActividad();
        return ResultadoValidacionSesion.ok(sesion);
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

    public java.util.Collection<SesionCliente> listarSesiones() {
        limpiarExpiradas();
        return java.util.Collections.unmodifiableCollection(sesionesPorUsername.values());
    }

    public synchronized void cerrarTodas() {
        sesionesPorUsername.clear();
    }

    private String normalizar(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private String extraerIp(String endpoint) {
        if (endpoint == null || endpoint.isBlank() || "desconocido".equalsIgnoreCase(endpoint)) {
            return "desconocido";
        }

        int separador = endpoint.lastIndexOf(':');
        if (separador <= 0) {
            return endpoint;
        }

        return endpoint.substring(0, separador);
    }

    private int extraerPuerto(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return -1;
        }

        int separador = endpoint.lastIndexOf(':');
        if (separador <= 0 || separador == endpoint.length() - 1) {
            return -1;
        }

        try {
            return Integer.parseInt(endpoint.substring(separador + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String describirOrigen(String ipRemitente, int puertoRemitente, String protocolo) {
        String endpoint = puertoRemitente > 0 ? ipRemitente + ":" + puertoRemitente : ipRemitente;
        return endpoint + " (" + protocolo + ")";
    }
}
