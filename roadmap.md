# 🚀 Roadmap de Desarrollo

## Sistema de Mensajería y Transferencia de Archivos (Cliente/Servidor)

---

## 🧠 Objetivo General

Construir un sistema distribuido cliente/servidor que permita el envío de mensajes y archivos (incluyendo archivos >1GB), soportando **TCP y UDP**, con almacenamiento, seguridad (hash + encriptación) y registro de logs.

---

## ⚙️ Stack Tecnológico (Propuesto)

### Servidor

* Java (consola)
* Sockets (TCP/UDP)
* JSON (Jackson)
* MySQL
* ExecutorService (Thread Pool)
* Log4j
* Crypto (SHA-256 + AES)

### Cliente

* JavaFX (recomendado) o Python
* Sockets TCP/UDP
* H2 Database

---

# 🗺️ Fases del Proyecto

---

## 🔹 FASE 0 — Setup y Configuración

### Objetivo

Preparar la estructura base del proyecto y configuración externa.

### Entregables

* Proyecto Maven inicializado
* Archivo `application.properties`
* Lectura de configuración

### Configuración ejemplo:

```properties
transport.protocol=TCP
server.port=8080
max.clients=10
```

---

## 🔹 FASE 1 — Capa de Transporte (TCP / UDP)

### Objetivo

Abstraer el protocolo de comunicación para permitir intercambiar entre TCP y UDP.

### Entregables

* Interfaz `Transport`
* Implementaciones:

    * `TcpTransport`
    * `UdpTransport`
* Factory o configuración dinámica basada en properties

### Validación

* Cambiar protocolo sin modificar lógica de negocio

---

## 🔹 FASE 2 — Protocolo de Comunicación (JSON)

### Objetivo

Definir el formato estándar de mensajes.

### Entregables

* Modelo base de mensaje
* Serialización/deserialización con JSON

### Ejemplo:

```json
{
  "type": "CONNECT | MESSAGE | FILE | LIST",
  "payload": {}
}
```

---

## 🔹 FASE 3 — Gestión de Clientes

### Objetivo

Registrar y administrar clientes conectados.

### Entregables

* `ClientRegistry`
* Registro de:

    * IP
    * Fecha
    * Hora de conexión

### Validación

* Cliente se conecta y aparece en consola

---

## 🔹 FASE 4 — Concurrencia y Object Pool

### Objetivo

Controlar el número máximo de clientes concurrentes.

### Entregables

* Thread Pool (`ExecutorService`)
* Límite de clientes
* Rechazo de conexiones

### Validación

* Conexiones superiores al límite son rechazadas

---

## 🔹 FASE 5 — Mensajería (Texto)

### Objetivo

Permitir el envío de mensajes entre clientes.

### Entregables

* `MessageService`
* Registro en logs

### Validación

* Cliente A envía mensaje a Cliente B correctamente

---

## 🔹 FASE 6 — Transferencia de Archivos (Base)

### Objetivo

Enviar y recibir archivos pequeños.

### Entregables

* Envío de archivos
* Guardado en disco

### Validación

* Archivo recibido correctamente

---

## 🔹 FASE 7 — Archivos Grandes (>1GB)

### Objetivo

Implementar transferencia eficiente por streaming.

### Entregables

* Uso de streams (`BufferedInputStream`)
* Transferencia por chunks

### Validación

* Archivo grande transferido sin errores ni consumo excesivo de memoria

---

## 🔹 FASE 8 — Seguridad

### Objetivo

Garantizar integridad y confidencialidad.

### Entregables

* Generación de hash (SHA-256)
* Encriptación (AES)

### Validación

* Hash coincide
* Archivo puede ser desencriptado correctamente

---

## 🔹 FASE 9 — Persistencia

### Objetivo

Guardar información en base de datos.

### Entregables

* Integración con MySQL
* Registro de:

    * nombre
    * tamaño
    * hash
    * propietario

### Validación

* Datos persistidos correctamente

---

## 🔹 FASE 10 — Servicios del Servidor

### Objetivo

Implementar funcionalidades requeridas.

### Entregables

* Conexión de clientes
* Listado de clientes
* Listado de documentos
* Envío de documentos:

    * normal
    * con hash
    * encriptado
* Recepción de documentos
* Logs

---

## 🔹 FASE 11 — Cliente

### Objetivo

Desarrollar aplicación cliente funcional.

### Entregables

* Conexión al servidor
* Envío/recepción de mensajes
* Envío de archivos (simultáneos)
* Visualización de logs

---

## 🔹 FASE 12 — Logging

### Objetivo

Registrar toda la actividad del sistema.

### Entregables

* Logs de:

    * conexiones
    * mensajes
    * archivos
    * errores

---

## 🔹 FASE 13 — Diagramas

### Objetivo

Documentar la arquitectura.

### Entregables

* Diagrama de componentes
* Diagrama de clases
* Diagrama de despliegue

---

# ⚠️ Riesgos Identificados

## UDP

* No garantiza entrega ni orden
* Posible pérdida de paquetes

## Archivos grandes

* Riesgo de consumo de memoria
* Corrupción de datos

## Concurrencia

* Race conditions
* Manejo de desconexiones

---

# 🧠 Estrategia de Desarrollo Recomendada

1. Implementar TCP primero
2. Implementar mensajería básica
3. Transferencia de archivos pequeños
4. Refactorizar a capa de transporte
5. Implementar UDP
6. Añadir concurrencia (pool)
7. Seguridad
8. Persistencia
9. Cliente
10. Logs y documentación

---

# 🏁 Estado del Proyecto

| Fase    | Estado | Notas |
| ------- | ------ | ----- |
| Fase 0  | ⬜      |       |
| Fase 1  | ⬜      |       |
| Fase 2  | ⬜      |       |
| Fase 3  | ⬜      |       |
| Fase 4  | ⬜      |       |
| Fase 5  | ⬜      |       |
| Fase 6  | ⬜      |       |
| Fase 7  | ⬜      |       |
| Fase 8  | ⬜      |       |
| Fase 9  | ⬜      |       |
| Fase 10 | ⬜      |       |
| Fase 11 | ⬜      |       |
| Fase 12 | ⬜      |       |
| Fase 13 | ⬜      |       |

---

# 🚀 Notas Finales

* Priorizar funcionalidad sobre perfección inicial
* Validar cada fase antes de avanzar
* Diseñar pensando en escalabilidad y desacoplamiento
* Mantener separación de responsabilidades (capas)

---
