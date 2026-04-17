# Guía de Transferencia Streaming de Archivos Grandes

## Descripción General

El sistema ahora soporta la transferencia de archivos **mayores de 1GB** sin agotar la memoria. El enfoque utiliza **streaming binario por chunks** en lugar de serializar el archivo completo como JSON + Base64.

## Características Principales

### ✅ TCP Streaming
- **Protocolo**: Frame binario binidireccional
- **Tamaño de chunk**: 4 MB (configurable)
- **Ventajas**: 
  - Entrega ordenada garantizada
  - Sin necesidad de ACK por chunk
  - Ideal para archivos muy grandes
  - Conexión persistente por transferencia

### ✅ UDP Streaming con Control Confiable
- **Protocolo**: Frame binario + ACK + Reintentos
- **Tamaño de chunk**: 2 MB (más pequeño que TCP para evitar fragmentación)
- **Características**:
  - ACK por cada chunk recibido
  - Detección automática de pérdida
  - Reintento hasta 5 veces por chunk
  - Timeout de 10 segundos por ACK
  - Ideal para archivos medianos a grandes

### ✅ Persistencia de Estado
- Rastreo de transferencias en BD (MySQL/MariaDB)
- Recuperación de transferencias interrumpidas
- Hash SHA-256 incremental (sin cargar archivo completo en memoria)
- Soporte para reanudación de transferencias

## Estructura Técnica

### Nuevos Archivos Creados

```
src/main/java/com/arquitectura/
├── comun/dto/
│   └── FrameTransferencia.java          // Frame binario para chunks
├── dominio/
│   ├── modelo/
│   │   └── TransferenciaStreamingModel.java   // Modelo de estado
│   └── repositorios/
│       ├── TransferenciaStreamingRepository.java
│       └── JpaTransferenciaStreamingRepository.java
├── infraestructura/transporte/
│   └── GestorTransferenciasStreaming.java     // Orquestador central
├── ejemplosClientes/
│   ├── tcp/
│   │   └── ClienteArchivoTCPStreaming.java    // Cliente TCP streaming
│   └── udp/
│       └── ClienteArchivoUDPStreaming.java    // Cliente UDP streaming
```

### Modificaciones a Archivos Existentes

1. **TcpProtocoloTransporte.java**: 
   - Detección automática de frames binarios vs JSON
   - Integración con GestorTransferenciasStreaming
   - Lectura eficiente de streams sin cargar todo en memoria

2. **UdpProtocoloTransporte.java**:
   - Soporte de frames binarios
   - Implementación de ACK por chunk
   - Reintentos automáticos
   - Cache de chunks recibidos

3. **persistence.xml**:
   - Registro de TransferenciaStreamingModel

## Cómo Usar

### 1. Preparar la Base de Datos



### 2. Cliente TCP Streaming

```java
import com.arquitectura.ejemplosClientes.tcp.ClienteArchivoTCPStreaming;
import java.nio.file.Path;

// Crear cliente
ClienteArchivoTCPStreaming cliente = new ClienteArchivoTCPStreaming("localhost", 5000);

// Enviar archivo grande (sin cargar completo en memoria)
cliente.enviarArchivoGrande(Path.of("ruta/archivo-1gb.zip"));
```

**Características**:
- ✓ No carga el archivo completo en RAM
- ✓ Usa buffer de 4 MB
- ✓ Hash SHA-256 incremental
- ✓ Progreso cada 10% de avance
- ✓ Logging detallado

### 3. Cliente UDP Streaming

```java
import com.arquitectura.ejemplosClientes.udp.ClienteArchivoUDPStreaming;
import java.nio.file.Path;

// Crear cliente
ClienteArchivoUDPStreaming cliente = new ClienteArchivoUDPStreaming("localhost", 5001);

// Enviar archivo con control confiable
cliente.enviarArchivoGrande(Path.of("ruta/archivo-500mb.zip"));
```

**Características**:
- ✓ ACK por cada chunk
- ✓ Reintentos automáticos (hasta 5 veces)
- ✓ Timeout de 10 segundos por ACK
- ✓ Detección de pérdidas
- ✓ No carga el archivo completo en RAM

### 4. Acceso al Gestor de Transferencias (desde el servidor)

```java
// Obtener desde TCP
TcpProtocoloTransporte tcp = (TcpProtocoloTransporte) transporte;
GestorTransferenciasStreaming gestor = tcp.getGestorTransferencias();

// Iniciar transferencia manualmente
String transferId = gestor.iniciarTransferencia(
    "archivo.zip",      // nombre
    1073741824L,         // tamaño (1 GB)
    262144L,             // totalChunks (4MB chunks)
    "cliente123",        // remitente
    "192.168.1.100",     // ipRemitente
    "TCP"                // protocolo
);

// Obtener estado
var estado = gestor.obtenerEstado(transferId);
if (estado.isPresent()) {
    System.out.println("Progreso: " + estado.get().getChunksRecibidos() + 
                      "/" + estado.get().getTotalChunks());
}

// Cancelar transferencia
gestor.cancelarTransferencia(transferId);

// Obtener estadísticas
var stats = gestor.obtenerEstadisticas();
System.out.println("Transferencias activas: " + stats.get("transferenciasActivas"));
System.out.println("Bytes en transferencia: " + stats.get("bytesEnTransferenciaMB") + " MB");
```

## Flujo de Transferencia

### TCP
```
Cliente                                  Servidor
   |                                        |
   |--- FrameTransferencia (chunk 0) ----->|
   |--- FrameTransferencia (chunk 1) ----->|
   |--- FrameTransferencia (chunk 2) ----->|
   |        ...                            | GestorTransferencias.procesarChunk()
   |--- FrameTransferencia (chunk N) ----->| - Escribe a archivo temporal
   |                                       | - Actualiza hash incremental
   |                                       | - Persiste estado en BD
   |                                       |
   |<-- (conexión cerrada) --------|      | Mueve a ubicación final
   |                                       | Limpia cache
```

### UDP
```
Cliente                                  Servidor
   |                                        |
   |--- FrameTransferencia (chunk 0) ----->|
   |<----- ACK (chunk 0) ---|               |
   |--- FrameTransferencia (chunk 1) ----->|
   |<----- ACK (chunk 1) ---|               |
   |        ...                            | GestorTransferencias.procesarChunk()
   |--- FrameTransferencia (chunk N) ----->| - Escribe a archivo temporal
   |<----- ACK (chunk N) ---|               | - ACK automático
   |                                       | - Detección de orden
```

## Configuración

### Límites Configurables (en GestorTransferenciasStreaming)

```java
// Máximo de transferencias concurrentes
private static final int MAX_TRANSFERENCIAS_CONCURRENTES = 100;

// Timeout de transferencia inactiva (1 hora)
private static final long TIMEOUT_TRANSFERENCIA_MS = 3600000;

// TCP - Tamaño de chunk
private static final int TAMAÑO_CHUNK = 4 * 1024 * 1024; // 4 MB

// UDP - Tamaño de chunk
private static final int TAMAÑO_CHUNK = 2 * 1024 * 1024; // 2 MB

// UDP - ACK timeout
private static final long ACK_TIMEOUT_MS = 10000; // 10 segundos

// UDP - Máximo reintentos
private static final int MAX_REINTENTOS = 5;
```

## Ventajas del Nuevo Sistema

### Memoria
- ✅ Sin picos de RAM por archivo completo
- ✅ Buffer fijo de 2-4 MB
- ✅ Streaming directo a disco
- ✅ Hash SHA-256 incremental

### Confiabilidad
- ✅ TCP: Entrega ordenada y confiable
- ✅ UDP: ACK + reintentos + timeout
- ✅ Persistencia de estado en BD
- ✅ Recuperación de fallos

### Rendimiento
- ✅ Sin serialización JSON completa
- ✅ Sin Base64 encoding overhead
- ✅ Streaming directo a disco
- ✅ Procesamiento paralelo de chunks

### Escalabilidad
- ✅ Control de concurrencia (máx 100 transferencias)
- ✅ Limpieza automática de transferencias expiradas
- ✅ Cache en memoria para transferencias activas
- ✅ BD para recuperación y auditoría

## Ejemplos de Uso

### Transferir archivo de 5 GB por TCP

```bash
# Compilar
mvn clean package

# Ejecutar servidor
java -cp target/JavaMensajeriaServidor-1.0-SNAPSHOT.jar com.arquitectura.Main

# En otra terminal, ejecutar cliente
java -cp target/JavaMensajeriaServidor-1.0-SNAPSHOT.jar \
     com.arquitectura.ejemplosClientes.tcp.ClienteArchivoTCPStreaming
```

### Transferir archivo de 2 GB por UDP con control

```bash
# Similar al TCP, pero usa ClienteArchivoUDPStreaming
java -cp target/JavaMensajeriaServidor-1.0-SNAPSHOT.jar \
     com.arquitectura.ejemplosClientes.udp.ClienteArchivoUDPStreaming
```

## Monitoreo

### Logs
Los logs incluyen:
- Inicio de transferencia (nombre, tamaño, totalChunks)
- Progreso cada N chunks (100 para TCP, 5 para UDP)
- Recepción de chunks y validación de hash
- Finalización y ubicación del archivo
- Errores y cancelaciones

### BD
Consultar estado de transferencias:

```sql
-- Transferencias activas
SELECT transfer_id, nombre_archivo, estado, chunks_recibidos, 
       total_chunks, offset_actual, (chunks_recibidos * 100 / total_chunks) as progreso
FROM transferencias_streaming
WHERE estado = 'IN_PROGRESS'
ORDER BY fecha_inicio DESC;

-- Estadísticas por protocolo
SELECT protocolo, COUNT(*) as total, 
       SUM(CASE WHEN estado = 'COMPLETED' THEN 1 ELSE 0 END) as completadas,
       AVG(chunks_recibidos) as promedio_chunks
FROM transferencias_streaming
GROUP BY protocolo;
```

## Resolución de Problemas

### UDP: "No se recibió ACK después de reintentos"
- Verificar conectividad entre cliente y servidor
- Aumentar `ACK_TIMEOUT_MS` si la red es lenta
- Verificar firewall permite UDP en puerto configurado

### Archivo no aparece después de transferencia
- Verificar directorio `archivos-recibidos/` existe
- Revisar logs del servidor para errores
- Consultar `transferencias_streaming` en BD para estado

### Alto consumo de memoria durante transferencia
- Reducir `TAMAÑO_CHUNK` (pero afecta rendimiento)
- Aumentar `MAX_TRANSFERENCIAS_CONCURRENTES` límite
- Revisar que archivos temporales se limpien

## Compatibilidad Hacia Atrás

El sistema mantiene **compatibilidad total** con el flujo JSON tradicional:

- ✓ Mensajes JSON se procesan como antes
- ✓ Transferencias pequeñas (< 20 MB) pueden seguir usando `ENVIAR_DOCUMENTO`
- ✓ Detección automática de frame binario vs JSON
- ✓ Sin cambios requeridos en clientes existentes

## Próximos Pasos Opcionales

1. **Compresión de chunks**: Implementar compresión gzip/deflate
2. **Encriptación**: Cifrar chunks con AES-256
3. **Verificación de integridad**: Validar hash por chunk vs cliente
4. **Reanudación mejorada**: Soporte para reanudar desde offset específico
5. **Métricas**: Exponer estadísticas por prometheus/micrometer

---

**Versión**: 1.0  
**Fecha**: 2026-04-17  
**Estado**: Producción

