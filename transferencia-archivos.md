# Guía técnica: soportar archivos >1GB sin agotar memoria

## Problema actual

El flujo actual serializa el archivo completo como JSON + Base64 en memoria. Esto genera varios picos de RAM:

1. Cliente lee archivo completo (`Files.readAllBytes`) y luego crea un `String` Base64.
2. Transporte TCP/UDP recibe todo el payload en memoria.
3. `ProcesadorMensajes` crea `String json` completo.
4. `EnviarArchivoHandler` vuelve a convertir el Base64 a `byte[]` completo.

Con archivos de 1 GB, Base64 crece ~33% (1 GB -> ~1.33 GB), por lo que el proceso termina fácilmente en OOM.

## Enfoque recomendado de transmisión

### 1) Mantener JSON solo para control

Usar mensajes JSON para:
- `INICIAR_TRANSFERENCIA`
- `ACK_CHUNK`
- `FINALIZAR_TRANSFERENCIA`
- `CANCELAR_TRANSFERENCIA`

Pero **no** enviar el contenido del archivo completo dentro de `payload.contenido`.

### 2) Enviar binario por stream en chunks

Para TCP:
- Mantener una conexión dedicada por archivo.
- Protocolo sugerido por frame:
    - `transferId` (UUID)
    - `indexChunk` (long)
    - `totalChunks` (long) opcional
    - `bytesChunk` (int)
    - `chunkData` (byte[])
    - `chunkSha256` (32 bytes) opcional
- Tamaño recomendado chunk: 256 KB a 4 MB.

Para UDP:
- **No recomendado** para >1GB salvo que implementes confiabilidad (reintento, ventana, orden, checksum por chunk).
- Si se requiere, usar segmentos pequeños y reensamble robusto.

## Cambios por clase (mapa de migración)

### `src/main/java/com/arquitectura/ejemplosClientes/tcp/ClienteArchivoTCPSimulado.java`
- Reemplazar `Files.readAllBytes(...)` por `BufferedInputStream`.
- Enviar archivo en chunks binarios, no en un único `payload.contenido`.

### `src/main/java/com/arquitectura/ejemplosClientes/udp/ClienteArchivoUDPSimulado.java`
- Evitar usar este cliente para archivos grandes en la versión inicial.
- Dejar UDP para archivos pequeños o implementar un protocolo confiable completo.

### `src/main/java/com/arquitectura/infraestructura/transporte/TcpProtocoloTransporte.java`
- Agregar método de recepción/envío orientado a stream binario.
- Evitar `BufferedReader.readLine()` para archivos grandes.
- Implementar framing binario (cabecera + datos por chunk).

### `src/main/java/com/arquitectura/infraestructura/transporte/UdpProtocoloTransporte.java`
- Mantener para mensajes de control o archivos pequeños.
- Si habrá binario grande por UDP: añadir ACK, ventana deslizante y reintentos.

### `src/main/java/com/arquitectura/aplicacion/ProcesadorMensajes.java`
- Separar ruta de procesamiento:
    - Ruta JSON (mensajería normal)
    - Ruta transferencia binaria por `transferId`
- No convertir el archivo completo a `String`.

### `src/main/java/com/arquitectura/dominio/handlers/EnviarArchivoHandler.java`
- Cambiar lógica para escribir chunks directamente a disco (`FileChannel` o `BufferedOutputStream`).
- Calcular SHA-256 incremental (`MessageDigest.update(...)` por chunk).
- Validar tamaño total y orden de chunks.
- Hacer reanudación por `transferId` (offset).

### `src/main/java/com/arquitectura/comun/dto/PaqueteDatos.java`
- Extender DTO o crear otro para frames binarios (metadatos + `byte[] chunk`).
- Evitar cargar el archivo completo en una única instancia.

### Persistencia (`ArchivoRecibidoModel` + repositorio)
- Guardar metadatos de transferencia:
    - `transferId`
    - `estado` (`IN_PROGRESS`, `COMPLETED`, `FAILED`)
    - `chunksRecibidos` / `offsetActual`
    - `hashFinal`
- No guardar el contenido completo cifrado en DB para archivos grandes.

## Estrategia de compatibilidad (sin romper clientes actuales)

1. Mantener `ENVIAR_DOCUMENTO` actual para archivos pequeños (ej. < 20 MB).
2. Agregar nuevo flujo `ENVIAR_DOCUMENTO_STREAM` para grandes.
3. Negociar capacidad al conectar (feature flag/protocolo).

## Recomendaciones de hardening

- Límites configurables:
    - `maxJsonPayloadBytes`
    - `maxChunkBytes`
    - `maxTransferRetries`
    - `maxConcurrentTransfers`
- Timeouts:
    - timeout por chunk
    - timeout total de transferencia
- Verificación final:
    - comparar hash final cliente/servidor
- Limpieza:
    - borrar archivos parciales expirados
- Observabilidad:
    - log de progreso cada N chunks

## Plan de implementación incremental

1. **Iteración 1 (TCP streaming básico):** framing binario + guardado por chunk + hash incremental.
2. **Iteración 2 (reanudación):** `transferId` + offset persistido.
3. **Iteración 3 (concurrencia):** pool dedicado y límites por cliente.
4. **Iteración 4 (UDP confiable opcional):** ACK + reintentos + ventana.

## Resumen ejecutivo

Para soportar archivos de más de 1GB sin que muera el programa, el cambio clave es **abandonar el envío de archivo completo en JSON/Base64** y migrar a **streaming binario por chunks**, idealmente sobre TCP, con hash incremental y persistencia de estado de transferencia.
