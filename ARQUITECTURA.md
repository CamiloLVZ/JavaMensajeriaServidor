# Arquitectura — JavaMensajeriaServidor

> Documento técnico exhaustivo generado a partir del análisis del código fuente.  
> Fecha: 2026-04-23

---

## Tabla de contenidos

1. [Arquitectura general](#1-arquitectura-general)
2. [Arranque del servidor](#2-arranque-del-servidor)
3. [Modelo de hilos y concurrencia](#3-modelo-de-hilos-y-concurrencia)
4. [Sistema de routing de mensajes](#4-sistema-de-routing-de-mensajes)
5. [Gestión de sesiones](#5-gestión-de-sesiones)
6. [Transferencia de archivos](#6-transferencia-de-archivos)
7. [Persistencia](#7-persistencia)
8. [Logging](#8-logging)

---

## 1. Arquitectura general

El servidor implementa una **Arquitectura Hexagonal** (también llamada Ports & Adapters) organizada en tres capas bien definidas:

```
┌─────────────────────────────────────────────────────────────────┐
│                        INFRAESTRUCTURA                          │
│  transporte/  │  persistencia/  │  logs/  │  seguridad/         │
│  concurrencia/│  serializacion/ │                               │
├─────────────────────────────────────────────────────────────────┤
│                         APLICACIÓN                              │
│  router/  │  sesion/  │  transferencia/  │  concurrencia/       │
│  ProcesadorMensajes  │  RespuestaSender  │  ContextoSolicitud   │
├─────────────────────────────────────────────────────────────────┤
│                          DOMINIO                                │
│  handlers/  │  repositorios/  │  modelo/                        │
└─────────────────────────────────────────────────────────────────┘
```

### 1.1 Capa de Dominio (`dominio/`)

Contiene la lógica de negocio pura. No depende de ninguna capa exterior.

| Paquete | Responsabilidad |
|---------|----------------|
| `handlers/` | Implementaciones del patrón Command/Handler: una clase por acción de negocio |
| `repositorios/` | Interfaces de repositorio (Port) + implementaciones JPA (Adapter) |
| `modelo/` | Entidades JPA: `MensajeModel`, `ArchivoRecibidoModel`, `LogServidorModel` |

### 1.2 Capa de Aplicación (`aplicacion/`)

Orquesta el flujo de una solicitud. Coordina dominio e infraestructura sin acoplarse a detalles técnicos.

| Clase / Paquete | Responsabilidad |
|-----------------|----------------|
| `ProcesadorMensajes` | Entry point de cada request: detecta protocolo, deserializa JSON, invoca el router |
| `ContextoSolicitud` | ThreadLocal con IP, puerto y protocolo del cliente actual |
| `RespuestaSender` | Envía la respuesta JSON al cliente (TCP o UDP) |
| `router/` | `MensajeRouter` + `Handler<T>` — despacha mensajes a handlers por `Accion` |
| `sesion/` | `GestorSesiones`, `SesionCliente` — estado de sesiones en memoria |
| `transferencia/` | `GestorTransferencias`, `GestorDescargasActivas`, Streams Emisor/Receptor |
| `concurrencia/` | `AtencionClienteTask` — tarea reutilizable del Object Pool |

### 1.3 Capa de Infraestructura (`infraestructura/`)

Implementaciones técnicas concretas. Todo lo que toca el sistema operativo, la red o la DB.

| Paquete | Responsabilidad |
|---------|----------------|
| `transporte/` | `ProtocoloTransporte` (interfaz) + `TcpProtocoloTransporte` + `UdpProtocoloTransporte` |
| `persistencia/` | `HibernateManager` — factory de `EntityManager` con HikariCP |
| `concurrencia/` | `ObjectPool<T>` — pool genérico de objetos reutilizables |
| `logs/` | `DatabaseLogHandler`, `LogConfig` — pipeline de logging a consola + DB |
| `serializacion/` | `JsonUtil` — wrapper sobre Jackson |
| `seguridad/` | `CryptoUtil` (SHA-256, AES), `CryptoConfig` |

### 1.4 Flujo completo de una solicitud

```
Cliente
  │
  │  [TCP: conexión]  /  [UDP: datagrama]
  ▼
ProtocoloTransporte.recibir()
  │  retorna PaqueteDatos
  ▼
Main (hilo principal)
  │  poolTareas.tomar(200ms timeout)
  ▼
ObjectPool<AtencionClienteTask>
  │  presta tarea disponible
  ▼
ExecutorService.execute(tarea)
  │
  │  (worker thread)
  ▼
AtencionClienteTask.run()
  │  → resolverPaquete() → leerDesdSocket() [solo TCP]
  │
  ▼
ProcesadorMensajes.procesar(paquete)
  │  → ContextoSolicitud.establecerOrigen()
  │  → deserializa JSON a Mensaje<?>
  ▼
MensajeRouter.responder(mensaje)
  │  → busca Handler por Accion
  │  → convierte payload al tipo correcto
  ▼
Handler<T>.handle(mensaje)        ← DOMINIO
  │  (ConectarHandler, MensajeTextoHandler, etc.)
  │  → valida sesión, persiste, etc.
  ▼
Respuesta<?> → JsonUtil.toJson()
  ▼
RespuestaSender.enviar()
  │  TCP: BufferedWriter al socket
  │  UDP: DatagramSocket.send()
  ▼
poolTareas.devolver(tarea)         ← Object Pool recupera la instancia
```

---

## 2. Arranque del servidor

### 2.1 `Main.java` — secuencia de inicialización

```
Main.main()
 │
 ├─ 1. LogConfig.configureRootLogger()
 │       Configura ConsoleHandler con formato [fecha][nivel][logger] mensaje
 │
 ├─ 2. application.properties → carga configuración:
 │       transfer-protocol  = TCP | UDP
 │       server.port        = ej. 9090
 │       max-clients        = ej. 10
 │       session.timeout.minutes = ej. 30
 │       mysql.url / mysql.user / mysql.password
 │       hibernate.hbm2ddl.auto = update
 │       db.pool.size       = ej. 10
 │
 ├─ 3. CryptoConfig.configurar(properties)
 │       Inicializa clave AES y parámetros de cifrado
 │
 ├─ 4. ConexionMySql.configurar(properties)
 │       Pasa propiedades a HibernateManager.inicializar()
 │       Crea EntityManagerFactory + HikariCP connection pool
 │       Valida conexión: SELECT 1
 │
 ├─ 5. LogConfig.configureDatabaseLogging()
 │       Agrega DatabaseLogHandler al root logger
 │       A partir de aquí todos los logs van también a MySQL
 │
 ├─ 6. ProtocoloTransporteFactory.crear(protocolo)
 │       → TcpProtocoloTransporte  (ServerSocket)
 │       → UdpProtocoloTransporte  (DatagramSocket)
 │    transporte.iniciar(puerto)
 │
 ├─ 7. MensajeRouterFactory.crearRouter()
 │       Registra los 11 handlers (ver sección 4)
 │
 ├─ 8. GestorSesiones.getInstance().configurar(maxClientes, timeout)
 │
 ├─ 9. ExecutorService = Executors.newFixedThreadPool(maxClientes)
 │       Pool fijo de maxClientes hilos worker
 │
 ├─ 10. ObjectPool<AtencionClienteTask> = new ObjectPool(maxClientes, AtencionClienteTask::new)
 │        Pre-crea maxClientes instancias de AtencionClienteTask
 │
 ├─ 11. Runtime.addShutdownHook()
 │        Al recibir SIGINT/SIGTERM:
 │        transporte.detener() → cierra socket
 │        ejecutorClientes.shutdown() → espera 5s, luego shutdownNow()
 │        GestorSesiones.cerrarTodas()
 │        ConexionMySql.cerrar() → cierra EntityManagerFactory
 │
 └─ 12. Loop principal:
         while(true)
           paquete = transporte.recibir()   // bloquea hasta nueva conexión/datagrama
           tarea   = poolTareas.tomar(200ms)
           if (tarea == null) → rechaza con JSON {"estado":"ERROR","error":{"codigo":"SERVIDOR_OCUPADO"}}
           tarea.preparar(paquete, procesador, sender, transporte, callback)
           ejecutorClientes.execute(tarea)
```

### 2.2 Configuración clave en `application.properties`

| Propiedad | Efecto |
|-----------|--------|
| `transfer-protocol=TCP` | Usa `TcpProtocoloTransporte` con `ServerSocket` |
| `transfer-protocol=UDP` | Usa `UdpProtocoloTransporte` con `DatagramSocket` |
| `max-clients=10` | Tamaño del `FixedThreadPool` Y del `ObjectPool` (sincronizados) |
| `session.timeout.minutes=30` | Tiempo de inactividad antes de expirar una sesión |
| `db.pool.size=10` | Tamaño máximo del pool HikariCP |
| `hibernate.hbm2ddl.auto=update` | Hibernate crea/actualiza tablas automáticamente |

---

## 3. Modelo de hilos y concurrencia

### 3.1 Diagrama de hilos

```
┌────────────────────────────────────────────────────────────────────┐
│  JVM Process                                                       │
│                                                                    │
│  ┌─────────────────┐                                               │
│  │   main thread   │ ◄── ServerSocket.accept() / DatagramSocket.receive()
│  │                 │     (bloqueante, libera CPU mientras espera)  │
│  │  Loop principal │                                               │
│  │  • recibir()    │                                               │
│  │  • tomar(200ms) │──► ObjectPool<AtencionClienteTask>            │
│  │  • execute()    │                      │                        │
│  └─────────────────┘                      │ presta instancia       │
│                                           ▼                        │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  FixedThreadPool (max-clients workers)                       │   │
│  │                                                             │   │
│  │  worker-1  │  worker-2  │  ...  │  worker-N                │   │
│  │  ──────────┼────────────┼───────┼──────────                │   │
│  │  AtencionClienteTask.run()                                  │   │
│  │  → leerDesdSocket()  (lectura real del TCP stream)          │   │
│  │  → procesador.procesar()                                    │   │
│  │  → sender.enviar()                                          │   │
│  │  → poolTareas.devolver(this)   ◄── retorna al pool          │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                    │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  streaming executor (CachedThreadPool, daemon)              │   │
│  │                                                             │   │
│  │  tcp-streaming-1  │  tcp-streaming-2  │  ...               │   │
│  │  StreamReceptorTcp.recibirArchivo()                         │   │
│  │  StreamEmisorTcp.emitirArchivo()                            │   │
│  │                                                             │   │
│  │  udp-streaming-1  │  udp-streaming-2  │  ...               │   │
│  │  StreamReceptorUdp.procesarChunk()                          │   │
│  │  StreamEmisorUdp.emitirArchivo()                            │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                    │
│  ┌─────────────────┐                                               │
│  │  db-log-writer  │ (SingleThreadExecutor, daemon)               │
│  │  DatabaseLogHandler → JpaLogServidorRepository                 │
│  └─────────────────┘                                               │
│                                                                    │
│  ┌─────────────────┐                                               │
│  │  shutdown-hook  │ (Thread de apagado limpio)                   │
│  └─────────────────┘                                               │
└────────────────────────────────────────────────────────────────────┘
```

### 3.2 `ObjectPool<T>` — `infraestructura/concurrencia/ObjectPool.java`

El pool es genérico y thread-safe. Usa `ArrayBlockingQueue` internamente.

```
ObjectPool<AtencionClienteTask>
  ├─ disponibles: ArrayBlockingQueue<AtencionClienteTask>(maxSize)
  ├─ Pre-rellena la cola en el constructor con maxSize instancias nuevas
  │
  ├─ tomar(timeout, unidad)
  │   └─ BlockingQueue.poll(timeout, unidad)
  │       retorna null si no hay disponibles en el plazo
  │       → Main detecta null → rechaza cliente con SERVIDOR_OCUPADO
  │
  └─ devolver(objeto)
      └─ BlockingQueue.offer(objeto)
          → libera al próximo hilo en espera
```

**Por qué Object Pool + FixedThreadPool con el mismo tamaño:**
- El `FixedThreadPool` limita cuántos hilos existen.
- El `ObjectPool` limita cuántas tareas se despachan _antes_ de que un hilo esté disponible.
- Si un worker tarda (ej. transferencia larga), el pool agota sus objetos y el Main rechaza nuevas conexiones inmediatamente (200ms timeout), sin bloquear el loop de accept/receive.

### 3.3 `AtencionClienteTask` — `aplicacion/concurrencia/AtencionClienteTask.java`

Implementa `Runnable`. **NO** se crea una instancia nueva por request — se reutiliza vía Object Pool.

```
Ciclo de vida de una AtencionClienteTask:

  [DISPONIBLE en pool]
       │
       │ tarea.preparar(paquete, procesador, sender, transporte, onFinish)
       ▼
  [PREPARADA]
       │
       │ ejecutorClientes.execute(tarea)
       ▼
  [EJECUTÁNDOSE en worker thread]
       │
       │ resolverPaquete()
       │   ├─ TCP sin datos: tcp.leerDesdSocket(socket)
       │   │    ├─ primer byte '{': lee JSON → retorna PaqueteDatos con data
       │   │    ├─ primer byte 0x02: manejarSubidaTcp() → streaming executor → retorna null
       │   │    └─ primer byte 0x03: manejarDescargaTcp() → streaming executor → retorna null
       │   └─ UDP: paquete ya tiene data (leído por el loop)
       │
       │ procesador.procesar(paqueteReal)
       │ sender.enviar(paqueteReal, respuesta, transporte)
       │
  [FINALIZANDO]
       │
       │ limpiarEstado()  → anula todas las referencias (paquete, procesador, sender, etc.)
       │ poolTareas.devolver(this)
       ▼
  [DISPONIBLE en pool]  ← listo para el próximo request
```

**Detalle crítico:** el callback `onFinish` se captura _antes_ de `limpiarEstado()` porque `limpiarEstado()` anula la referencia a `onFinish`. Si se hiciera al revés, el pool nunca recuperaría la tarea.

### 3.4 `ContextoSolicitud` — datos per-request en ThreadLocal

```java
ThreadLocal<String>  IP_REMITENTE
ThreadLocal<Integer> PUERTO_REMITENTE
ThreadLocal<String>  PROTOCOLO
```

Cada worker thread tiene su propia copia. Se establece al inicio de `ProcesadorMensajes.procesar()` y se limpia en el bloque `finally`. Los handlers (en dominio) leen estos valores para validar sesiones y para que `DatabaseLogHandler` sepa qué IP asociar al log.

---

## 4. Sistema de routing de mensajes

### 4.1 Diagrama del routing

```
ProcesadorMensajes.procesar(paquete)
         │
         │ resolverMensaje(json)
         │   ├─ accion == "CONECTAR" → convertirRegistroPlano()
         │   │    construye Mensaje<PayloadConectar> manualmente
         │   └─ resto → JsonUtil.fromJson(json, Mensaje.class)
         │
         ▼
  Mensaje<?> { tipo, accion, metadata, payload }
         │
         ▼
MensajeRouter.responder(mensaje)
         │
         │ handlers.get(mensaje.getAccion())
         │
         ├─ null → Respuesta ERROR "ACCION_NO_SOPORTADA"
         │
         └─ handler encontrado
               │
               ▼
         ejecutarHandler(handler, mensaje)
               │
               │ JsonUtil.convert(mensaje.getPayload(), handler.getPayloadClass())
               │    convierte el payload genérico al tipo específico del handler
               │
               ▼
         handler.handle(mensajeTipado)
               │
               └─ retorna Respuesta<?>
```

### 4.2 Interfaz `Handler<T>`

```java
public interface Handler<T> {
    Respuesta<?> handle(Mensaje<T> mensaje);
    Class<T> getPayloadClass();   // necesario para la conversión de tipo segura
}
```

El método `getPayloadClass()` es clave: permite al router convertir el payload genérico (que llega como `Object` tras la deserialización JSON) al tipo concreto que el handler necesita, usando `JsonUtil.convert()`.

### 4.3 Tabla de acciones y handlers registrados

| `Accion` (enum) | Handler | Payload de entrada | Descripción |
|-----------------|---------|-------------------|-------------|
| `CONECTAR` | `ConectarHandler` | `PayloadConectar` | Registra sesión en `GestorSesiones` |
| `DESCONECTAR` | `DesconectarHandler` | `Object` | Elimina sesión del gestor |
| `ENVIAR_MENSAJE` | `MensajeTextoHandler` | `PayloadEnviarMensaje` | Persiste mensaje en DB (texto plano + hash + cifrado) |
| `ENVIAR_DOCUMENTO` | `EnviarArchivoHandler` | `PayloadEnviarArchivo` | Recibe archivo en Base64, lo guarda en disco y DB |
| `LISTAR_MENSAJES` | `ListarMensajesHandler` | — | Consulta todos los mensajes de la DB |
| `LISTAR_DOCUMENTOS` | `ListarDocumentosHandler` | — | Consulta todos los archivos de la DB |
| `LISTAR_LOGS` | `ListarLogsHandler` | — | Consulta logs del servidor de la DB |
| `LISTAR_CLIENTES` | `ListarClientesHandler` | — | Lista sesiones activas en memoria |
| `INICIAR_STREAM` | `IniciarStreamHandler` | `PayloadIniciarStream` | Prepara transferencia chunked: crea `.tmp`, registra en `GestorTransferencias` |
| `FINALIZAR_STREAM` | `FinalizarStreamHandler` | `PayloadFinalizarStream` | Valida hash SHA-256, mueve `.tmp` al nombre final, persiste en DB |
| `SOLICITAR_STREAM` | `ObtenerArchivoHandler` | `PayloadSolicitarStream` | Autoriza descarga: registra en `GestorDescargasActivas`, responde con `transferId` |

### 4.4 `MensajeRouterFactory` — registro de handlers

Los handlers se registran una sola vez al arrancar:

```java
MensajeRouter router = new MensajeRouter();
router.registrarHandler(Accion.CONECTAR,         new ConectarHandler());
router.registrarHandler(Accion.DESCONECTAR,       new DesconectarHandler());
// ... (11 en total)
```

El `Map<Accion, Handler<?>>` es inmutable en tiempo de ejecución — no hay sincronización en lectura porque el mapa nunca cambia después del arranque.

---

## 5. Gestión de sesiones

### 5.1 `GestorSesiones` — Singleton thread-safe

```
GestorSesiones (Singleton)
  ├─ sesionesPorUsername: ConcurrentHashMap<String, SesionCliente>
  ├─ lock: ReentrantReadWriteLock
  ├─ maxSesiones: volatile int
  └─ timeoutInactividad: volatile Duration
```

**Estrategia de concurrencia:**
- `ConcurrentHashMap` para acceso concurrente seguro en lecturas puras.
- `ReentrantReadWriteLock` (write lock) para mutaciones del mapa (registrar, eliminar, validar) porque muchas operaciones son compuestas (check-then-act) y deben ser atómicas.
- La limpieza de sesiones expiradas (`limpiarExpiradasInterno()`) ocurre _dentro_ del write lock antes de cada registro/validación — **lazy expiration** sin scheduler externo.

### 5.2 `SesionCliente` — estado de una sesión

```java
public class SesionCliente {
    private final String  username;        // inmutable
    private final Instant creadoEn;       // inmutable
    private volatile String  ipRemitente;
    private volatile int     puertoRemitente;
    private volatile String  protocolo;
    private volatile Instant ultimoAcceso;
}
```

Los campos mutables son `volatile` para visibilidad sin bloqueo en lecturas simples.

### 5.3 Flujo de registro

```
registrar(username, ip, puerto, protocolo)
  │
  ├─ writeLock()
  ├─ limpiarExpiradasInterno()
  │
  ├─ username vacío → error "USERNAME_INVALIDO"
  │
  ├─ existe sesión?
  │    ├─ mismaConexion(ip, puerto, protocolo) → marcarActividad() → ok (idempotente)
  │    ├─ mismoCanalLogico(ip, protocolo) → actualizarOrigen() → reconexión
  │    └─ diferente cliente → error "USERNAME_YA_REGISTRADO"
  │
  ├─ size >= maxSesiones → error "MAX_SESIONES_ALCANZADO"
  │
  └─ new SesionCliente() → put() → ok "Sesion registrada"
```

### 5.4 Flujo de validación (en cada operación protegida)

```
validarSesion(username, ip, puerto, protocolo)
  │
  ├─ writeLock()  (puede actualizar puerto UDP)
  ├─ limpiarExpiradasInterno()
  │
  ├─ sesión no existe → error "SESION_NO_REGISTRADA"
  │
  ├─ aceptaOperacionDesde(ip, puerto, protocolo)
  │    ├─ TCP: verifica solo IP + protocolo (puerto cambia por conexión efímera)
  │    └─ UDP: verifica IP + protocolo, actualiza puerto efímero si cambió
  │
  ├─ no acepta → error "ORIGEN_SESION_INVALIDO"
  │
  └─ marcarActividad() → ok (SesionCliente)
```

### 5.5 `ResultadoRegistroSesion` y `ResultadoValidacionSesion`

Ambos son **Java Records** (inmutables). Encapsulan el resultado de una operación sin lanzar excepciones de control de flujo:

```java
record ResultadoRegistroSesion(
    boolean exito, String codigoError, String mensaje,
    SesionCliente sesion, boolean reconexion)

record ResultadoValidacionSesion(
    boolean exito, String codigoError, String mensaje,
    SesionCliente sesion)
```

---

## 6. Transferencia de archivos

El servidor soporta dos modalidades de transferencia:

| Modalidad | Mecanismo | Cuándo usar |
|-----------|-----------|-------------|
| **Inline Base64** | JSON + `ENVIAR_DOCUMENTO` | Archivos pequeños (MB) |
| **Streaming chunked** | Protocolo binario fuera de banda | Archivos grandes (decenas/cientos de MB) |

### 6.1 Protocolo de detección por primer byte

Tanto TCP como UDP inspeccionan el **primer byte** de cada conexión/datagrama:

```
Primer byte   Significado
────────────────────────────────────────
0x7B  '{'    Mensaje JSON de control → flujo normal (router)
0x02  STX    Chunk de upload (cliente → servidor)
0x03  ETX    Señal de download (cliente solicita chunks al servidor)
0x01         ACK de descarga (solo UDP, cliente confirma chunk)
0x00         NACK de descarga (solo UDP)
```

### 6.2 Upload de archivos — flujo completo

#### Fase de control (JSON)

```
Cliente                                    Servidor
  │                                            │
  │──── INICIAR_STREAM (JSON) ──────────────►  │
  │     { accion: "INICIAR_STREAM",            │  IniciarStreamHandler:
  │       payload: {                           │  1. Valida sesión
  │         transferId: "uuid",               │  2. Crea directorio "archivos-recibidos/"
  │         nombreArchivo: "foto.jpg",        │  3. Crea archivo vacío uuid.tmp
  │         extension: "jpg",                 │  4. GestorTransferencias.registrar()
  │         tamanoTotal: 5242880,             │  5. Responde con transferId confirmado
  │         totalChunks: 3 } }               │
  │                                            │
  │◄─── { estado: "EXITO", payload: "uuid" } ──│
```

#### Fase de streaming binario (fuera de banda)

```
Cliente                                    Servidor
  │                                            │
  │── [nueva conexión TCP o datagrama UDP] ──► │
  │   primer byte = 0x02                       │
  │                                            │  TcpProtocoloTransporte.leerDesdSocket()
  │                                            │  detecta 0x02 → manejarSubidaTcp()
  │                                            │  → streamingExecutor.submit(
  │                                            │       StreamReceptorTcp.recibirArchivo)
  │                                            │
  │  Frame binario por chunk:                  │
  │  ┌──────────┬──────────┬─────────┬──────┐  │
  │  │ id(36B)  │ idx(8B)  │ sz(4B)  │data │  │
  │  └──────────┴──────────┴─────────┴──────┘  │
  │                                            │
  │──────────────── Chunk 0 ─────────────────► │  StreamReceptorTcp:
  │                                            │  1. Lee header (48 bytes)
  │                                            │  2. Lee chunkSize bytes
  │                                            │  3. GestorTransferencias.obtener(transferId)
  │                                            │  4. FileChannel.write() → uuid.tmp (append)
  │                                            │  5. digest.update(datos) → SHA-256 incremental
  │                                            │  6. registrarChunk()
  │◄──────────── ACK (0x01) ──────────────────│
  │                                            │
  │──────────────── Chunk 1 ─────────────────► │  (mismo proceso)
  │◄──────────── ACK (0x01) ──────────────────│
  │  ... (stop-and-wait) ...                   │
```

#### Fase de finalización (JSON)

```
  │──── FINALIZAR_STREAM (JSON) ────────────►  │
  │     { payload: {                           │  FinalizarStreamHandler:
  │         transferId: "uuid",               │  1. Valida sesión
  │         hashSha256: "base64..." } }       │  2. GestorTransferencias.obtener()
  │                                            │  3. estado.hashFinalBase64() vs payload.hash
  │                                            │  4. Si mismatch → elimina .tmp → error HASH_INVALIDO
  │                                            │  5. Si ok → Files.move(uuid.tmp → nombre.ext)
  │                                            │  6. repositorio.guardar() → DB
  │                                            │  7. GestorTransferencias.eliminar(transferId)
  │◄─── { estado: "EXITO" } ──────────────────│
```

### 6.3 Download de archivos — flujo completo

```
Cliente                                    Servidor
  │                                            │
  │──── SOLICITAR_STREAM (JSON) ────────────►  │
  │     { payload: { archivoId: "uuid" } }     │  ObtenerArchivoHandler:
  │                                            │  1. Valida sesión
  │                                            │  2. JpaArchivoRecibidoRepository.buscarPorId()
  │                                            │  3. Verifica que el archivo existe en disco
  │                                            │  4. Determina chunkSize:
  │                                            │     TCP: 2 MB | UDP: 60 KB
  │                                            │  5. Genera nuevo transferId (UUID)
  │                                            │  6. GestorDescargasActivas.registrar(transferId, ruta, chunkSize)
  │◄─── PayloadIniciarDescarga ───────────────│
  │     { transferId, nombreArchivo,           │
  │       extension, tamano, totalChunks,      │
  │       chunkSize, hashSha256 }              │
  │                                            │
  │  [nueva conexión TCP o datagrama UDP]      │
  │  primer byte = 0x03                        │
  │  + transferId (36 bytes)                   │
  │────────────────────────────────────────►   │  TcpProtocoloTransporte:
  │                                            │  detecta 0x03 → manejarDescargaTcp()
  │                                            │  → streamingExecutor.submit(
  │                                            │       StreamEmisorTcp.emitirArchivo)
  │                                            │
  │                                            │  StreamEmisorTcp:
  │                                            │  1. Lee transferId (36 bytes)
  │                                            │  2. GestorDescargasActivas.obtener(transferId)
  │                                            │  3. Abre FileChannel en modo READ
  │◄──────────── Chunk 0 ─────────────────────│
  │  ┌──────────┬──────────┬─────────┬──────┐  │
  │  │ id(36B)  │ idx(8B)  │ sz(4B)  │data │  │
  │  └──────────┴──────────┴─────────┴──────┘  │
  │──────────────── ACK (0x01) ──────────────► │  servidor espera ACK antes de enviar
  │◄──────────── Chunk 1 ─────────────────────│  siguiente chunk (stop-and-wait)
  │  ...                                       │
  │                                            │  StreamEmisorTcp:
  │                                            │  GestorDescargasActivas.eliminar(transferId)
```

### 6.4 Diferencias UDP en streaming

```
UDP Upload (StreamReceptorUdp):
  ├─ Stateless: procesarChunk() recibe un DatagramPacket completo
  ├─ synchronized(estado) para escrituras concurrentes por transferencia
  ├─ Limite de chunk: 60 000 bytes (margen seguro del MTU de ~65 467 bytes)
  └─ ACK: 9 bytes = 0x01 + chunkIndex(long big-endian)
      enviado de vuelta por socket.send() al cliente

UDP Download (StreamEmisorUdp):
  ├─ Problema: el loop de UdpProtocoloTransporte comparte el mismo DatagramSocket
  │  No puede llamar socket.receive() sin competir con el loop principal
  ├─ Solución: GestorDescargasActivas.colaAcks (LinkedBlockingQueue)
  │  El loop detecta datagramas 0x01/0x00 y los deposita en la cola
  │  StreamEmisorUdp los consume con recibirAck(ACK_TIMEOUT_MS)
  ├─ Retransmisión: hasta MAX_RETRIES=5 intentos por chunk antes de abortar
  └─ ACK_TIMEOUT_MS=5000ms por chunk
```

### 6.5 `GestorTransferencias` — estado de uploads en curso

```
GestorTransferencias (Singleton)
  └─ transferencias: ConcurrentHashMap<String, EstadoTransferencia>

EstadoTransferencia {
  transferId, nombreArchivo, extension
  tamanoTotal, totalChunks
  rutaTemporal: Path         // ej. archivos-recibidos/uuid.tmp
  inicio: Instant
  digest: MessageDigest      // SHA-256 incremental, synchronized
  chunksRecibidos, bytesRecibidos
}
```

El `digest` es clonado para consultas intermedias (`hashActualBase64()`) y finalizado solo una vez al completarse (`hashFinalBase64()`).

### 6.6 `GestorDescargasActivas` — descargas autorizadas pendientes

```
GestorDescargasActivas (Singleton)
  ├─ descargas: ConcurrentHashMap<String, DescargaAutorizada>
  └─ colaAcks:  LinkedBlockingQueue<byte[]>   ← ACK router para UDP

DescargaAutorizada { transferId, rutaArchivo, chunkSize }
```

---

## 7. Persistencia

### 7.1 `HibernateManager`

Punto único de acceso a JPA. Inicializado una sola vez al arranque.

```
HibernateManager.inicializar(properties)
  │
  ├─ Crea Map<String,Object> con configuración dinámica:
  │   jakarta.persistence.jdbc.url/user/password
  │   hibernate.dialect = MySQLDialect
  │   hibernate.hbm2ddl.auto = update (crea/modifica tablas automáticamente)
  │
  ├─ HikariCP connection pool:
  │   hibernate.connection.provider_class = HikariCPConnectionProvider
  │   hibernate.hikari.minimumIdle = 2
  │   hibernate.hikari.maximumPoolSize = db.pool.size (default 10)
  │   hibernate.hikari.connectionTimeout = 5000ms
  │   hibernate.hikari.idleTimeout = 60000ms
  │
  ├─ Persistence.createEntityManagerFactory("mensajeriaPU", config)
  │   Persistence Unit "mensajeriaPU" definido en persistence.xml
  │   Mapea: MensajeModel, ArchivoRecibidoModel, LogServidorModel
  │
  └─ validarConexion() → EntityManager.createNativeQuery("SELECT 1")
      Lanza IllegalStateException si MySQL no está disponible

HibernateManager.crearEntityManager()
  └─ entityManagerFactory.createEntityManager()
      Devuelve una conexión del pool HikariCP
      IMPORTANTE: el caller es responsable de cerrar el EntityManager
```

### 7.2 Entidades JPA

#### `MensajeModel` → tabla `mensajes`

| Columna | Tipo JPA | Descripción |
|---------|---------|-------------|
| `id` | `@Id` String(36) | UUID del mensaje |
| `autor` | String(255) | Username del remitente |
| `ip_remitente` | String(45) | IP de origen (IPv4/IPv6) |
| `contenido` | TEXT | Texto plano del mensaje |
| `hash_sha256` | String(88) | SHA-256 en Base64 para verificar integridad |
| `contenido_cifrado` | LONGTEXT | AES encrypt del contenido en Base64 |
| `fecha_envio` | LocalDateTime | Timestamp del mensaje |

#### `ArchivoRecibidoModel` → tabla `archivos`

| Columna | Tipo JPA | Descripción |
|---------|---------|-------------|
| `id` | `@Id` String(36) | UUID del archivo |
| `remitente` | String(255) | Username del remitente |
| `ip_remitente` | String(45) | IP de origen |
| `nombre_archivo` | String(255) | Nombre sin extensión |
| `extension` | String(50) | Extensión del archivo |
| `ruta_archivo` | TEXT | Ruta absoluta en disco del servidor |
| `hash_sha256` | String(88) | SHA-256 para verificar integridad |
| `contenido_cifrado` | LONGTEXT | AES encrypt (vacío para archivos grandes por streaming) |
| `tamano` | long | Tamaño en bytes |
| `fecha_recepcion` | LocalDateTime | Timestamp de recepción |

#### `LogServidorModel` → tabla `logs_servidor`

| Columna | Tipo JPA | Descripción |
|---------|---------|-------------|
| `id` | `@Id @GeneratedValue IDENTITY` | Long autoincremental |
| `nivel` | String(20) | SEVERE, WARNING, INFO, FINE, etc. |
| `mensaje` | TEXT | Mensaje formateado + stacktrace si hay excepción |
| `origen` | String(100) | `getLoggerName()` — nombre de la clase que logueó |
| `ip_remitente` | String(45) | IP del cliente en contexto (ThreadLocal) |
| `fecha_evento` | LocalDateTime | Timestamp del log |

### 7.3 Repositorios — patrón Repository

Cada entidad tiene una interfaz (Port) y una implementación JPA (Adapter):

```
MensajeRepository (interface)
  └─ JpaMensajeRepository (implements)
       ├─ guardar(...)           → persist + commit
       └─ listarTodos()         → JPQL ORDER BY fecha_envio DESC

ArchivoRecibidoRepository (interface)
  └─ JpaArchivoRecibidoRepository (implements)
       ├─ guardar(...)           → persist + commit
       ├─ listarTodos()         → JPQL ORDER BY fecha_recepcion DESC
       └─ buscarPorId(id)       → em.find() → Optional<ArchivoRecibidoModel>

LogServidorRepository (interface)
  └─ JpaLogServidorRepository (implements)
       ├─ guardar(...)           → persist + commit
       ├─ listarTodos()         → JPQL ORDER BY fecha_evento DESC
       ├─ listarPaginado(p, sz) → setFirstResult/setMaxResults
       └─ contarTotal()         → COUNT JPQL
```

**Patrón de transacción en todos los repos:**

```java
EntityManager em = HibernateManager.crearEntityManager();
EntityTransaction tx = em.getTransaction();
try {
    tx.begin();
    em.persist(entity);
    tx.commit();
} catch (Exception e) {
    if (tx.isActive()) tx.rollback();
    throw new IllegalStateException("...", e);
} finally {
    em.close();   // ← SIEMPRE, devuelve la conexión al pool HikariCP
}
```

---

## 8. Logging

### 8.1 Pipeline de logging

```
Cualquier clase → Logger.getLogger(NombreClase.class.getName())
                         │
                         │ Logger.log(level, mensaje)
                         ▼
                  Root Logger (nivel INFO)
                         │
              ┌──────────┴──────────┐
              ▼                     ▼
     ConsoleHandler          DatabaseLogHandler
     (formato texto)         (async → MySQL)
```

### 8.2 `LogConfig` — configuración del pipeline

```
LogConfig.configureRootLogger()   ← llamado al inicio de Main
  ├─ Elimina handlers por defecto del root logger
  └─ Agrega ConsoleHandler con formato personalizado:
       [YYYY-MM-DD HH:mm:ss] [LEVEL  ] [logger.name] mensaje

LogConfig.configureDatabaseLogging()   ← llamado después de inicializar MySQL
  └─ Agrega DatabaseLogHandler al root logger
      (double-checked locking con volatile boolean para thread-safety)
```

### 8.3 `DatabaseLogHandler` — flujo async

```
Worker thread genera log:
  Logger.info("mensaje")
         │
         ▼
  DatabaseLogHandler.publish(LogRecord record)
         │
         │ Guard: EN_PUBLICACION.get() == false
         │   (evita recursión infinita si el repositorio también loguea)
         │
         │ Captura AHORA (en el hilo del caller):
         │   nivel    = record.getLevel().getName()
         │   mensaje  = formatter.format(record)   [incluye stacktrace si hay excepción]
         │   origen   = record.getLoggerName()
         │   ip       = ContextoSolicitud.obtenerIpRemitente()  ← ThreadLocal
         │   ts       = LocalDateTime.ofInstant(...)
         │
         ▼
  logExecutor.submit(() → {    ← SingleThreadExecutor "db-log-writer"
    EN_PUBLICACION.set(true)   ← flag re-entrancia en el log thread
    logServidorRepository.guardar(nivel, mensaje, origen, ip, ts)
    EN_PUBLICACION.remove()
  })
         │
         └─ El worker thread continúa SIN esperar el INSERT a MySQL
```

**Por qué capturar los datos antes de ir async:**
- `LogRecord` es mutable — Hibernate/el pool pueden modificarlo después.
- `ContextoSolicitud` es ThreadLocal del worker thread — en el hilo de `db-log-writer` la IP sería null si no se copia antes.

**Por qué `EN_PUBLICACION` ThreadLocal:**
- Si `JpaLogServidorRepository.guardar()` lanza una excepción, el sistema intentaría loguearla → nuevo `publish()` → recursión infinita.
- El flag ThreadLocal (separado por hilo) rompe el ciclo: el hilo de log ignora registros generados por sí mismo.

### 8.4 Niveles de log usados

| Nivel | Cuándo |
|-------|--------|
| `SEVERE` | Errores críticos (error en DB, exception no manejada en worker) |
| `WARNING` | Situaciones anómalas (cliente sin sesión, NACK en streaming, socket cerrado) |
| `INFO` | Eventos de negocio normales (conexión, mensaje recibido, archivo guardado) |
| `FINE` | Detalles de bajo nivel (chunks enviados/recibidos, pool de objetos) |

---

## Apéndice A — Seguridad

| Mecanismo | Implementación | Cuándo se aplica |
|-----------|---------------|-----------------|
| Hash SHA-256 | `CryptoUtil.sha256Base64()` | Todo mensaje y archivo antes de persistir |
| Cifrado AES | `CryptoUtil.aesEncryptBase64()` | Mensajes de texto y archivos inline (NO en streaming grande) |
| Validación de sesión | `GestorSesiones.validarSesion()` | En todos los handlers excepto `ConectarHandler` |
| Validación de origen | `SesionCliente.aceptaOperacionDesde()` | Detecta si la IP coincide con la sesión registrada |

---

## Apéndice B — Estructura de paquetes completa

```
com.arquitectura/
├─ Main.java
├─ aplicacion/
│   ├─ ProcesadorMensajes.java
│   ├─ ContextoSolicitud.java
│   ├─ RespuestaSender.java
│   ├─ concurrencia/
│   │   └─ AtencionClienteTask.java
│   ├─ router/
│   │   ├─ Handler.java                (interface)
│   │   ├─ MensajeRouter.java
│   │   └─ MensajeRouterFactory.java
│   ├─ sesion/
│   │   ├─ GestorSesiones.java
│   │   ├─ SesionCliente.java
│   │   ├─ ResultadoRegistroSesion.java    (record)
│   │   └─ ResultadoValidacionSesion.java  (record)
│   └─ transferencia/
│       ├─ GestorTransferencias.java
│       ├─ GestorDescargasActivas.java
│       ├─ StreamReceptorTcp.java
│       ├─ StreamEmisorTcp.java
│       ├─ StreamReceptorUdp.java
│       └─ StreamEmisorUdp.java
├─ dominio/
│   ├─ handlers/
│   │   ├─ ConectarHandler.java
│   │   ├─ DesconectarHandler.java
│   │   ├─ MensajeTextoHandler.java
│   │   ├─ EnviarArchivoHandler.java
│   │   ├─ IniciarStreamHandler.java
│   │   ├─ FinalizarStreamHandler.java
│   │   ├─ ObtenerArchivoHandler.java
│   │   ├─ ListarMensajesHandler.java
│   │   ├─ ListarDocumentosHandler.java
│   │   ├─ ListarLogsHandler.java
│   │   └─ ListarClientesHandler.java
│   ├─ modelo/
│   │   ├─ MensajeModel.java
│   │   ├─ ArchivoRecibidoModel.java
│   │   └─ LogServidorModel.java
│   └─ repositorios/
│       ├─ MensajeRepository.java           (interface)
│       ├─ JpaMensajeRepository.java
│       ├─ ArchivoRecibidoRepository.java   (interface)
│       ├─ JpaArchivoRecibidoRepository.java
│       ├─ LogServidorRepository.java       (interface)
│       └─ JpaLogServidorRepository.java
├─ infraestructura/
│   ├─ concurrencia/
│   │   └─ ObjectPool.java
│   ├─ logs/
│   │   ├─ DatabaseLogHandler.java
│   │   └─ LogConfig.java
│   ├─ persistencia/
│   │   ├─ HibernateManager.java
│   │   └─ ConexionMySql.java
│   ├─ seguridad/
│   │   ├─ CryptoUtil.java
│   │   └─ CryptoConfig.java
│   ├─ serializacion/
│   │   └─ JsonUtil.java
│   └─ transporte/
│       ├─ ProtocoloTransporte.java         (interface)
│       ├─ ProtocoloTransporteFactory.java
│       ├─ TcpProtocoloTransporte.java
│       └─ UdpProtocoloTransporte.java
└─ comun/
    └─ dto/
        └─ PaqueteDatos.java
```

---

## Apéndice C — Formato del protocolo binario de streaming

### Frame de chunk (upload y download)

```
Offset  Longitud  Tipo       Descripción
──────────────────────────────────────────────────────────────
0       36 B      UTF-8      transferId (UUID con guiones, padding con 0x00 si < 36)
36       8 B      long BE    chunkIndex (índice del chunk, base 0)
44       4 B      int BE     chunkSize  (bytes de datos en este frame)
48      chunkSize byte[]     datos del chunk
```

### ACK/NACK (TCP: 1 byte, UDP: 9 bytes)

```
TCP:
  0x01 = ACK   (el receptor está listo para el siguiente chunk)
  0x00 = NACK  (error en el chunk, el emisor aborta)

UDP:
  Offset  Longitud  Descripción
  0       1 B       0x01 = ACK | 0x00 = NACK
  1       8 B       chunkIndex confirmado (long BE)
```

### Señales de inicio de conexión TCP

```
Primer byte  Siguiente contenido
──────────────────────────────────────────────────
0x7B '{'     JSON de control (resto de la línea)
0x02         Chunks de upload a continuación
0x03         transferId (36 bytes), luego espera chunks del servidor
```
