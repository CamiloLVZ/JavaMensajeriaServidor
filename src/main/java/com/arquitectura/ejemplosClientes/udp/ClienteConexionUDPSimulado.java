package com.arquitectura.ejemplosClientes.udp;

import com.arquitectura.infraestructura.serializacion.JsonUtil;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Protocolo;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadConectar;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

public class ClienteConexionUDPSimulado {
    public static void main(String[] args) {

        String host = "localhost";
        int puertoServidor = 8080;

        try (DatagramSocket socket = new DatagramSocket()) {

            System.out.println("[CLIENTE-UDP] Iniciado");

            // 🔥 Crear mensaje
            Mensaje<PayloadConectar> mensaje = new Mensaje<>();
            mensaje.setTipo(TipoMensaje.REQUEST);
            mensaje.setAccion(Accion.CONECTAR);

            Metadata meta = new Metadata();
            meta.setIdMensaje(UUID.randomUUID().toString());
            meta.setTimestamp(LocalDateTime.now());
            meta.setProtocolo(Protocolo.UDP);

            mensaje.setMetadata(meta);

            PayloadConectar payload = new PayloadConectar();
            payload.setUsername("juan");

            mensaje.setPayload(payload);

            // 🔥 Serializar
            String json = JsonUtil.toJson(mensaje);
            byte[] data = json.getBytes(StandardCharsets.UTF_8);

            System.out.println("[CLIENTE-UDP] Enviando:");
            System.out.println(json);

            // 🔥 Enviar datagrama
            InetAddress address = InetAddress.getByName(host);

            DatagramPacket paqueteEnvio = new DatagramPacket(
                    data,
                    data.length,
                    address,
                    puertoServidor
            );

            socket.send(paqueteEnvio);

            System.out.println("[CLIENTE-UDP] Mensaje enviado");

            // 🔥 Preparar buffer para respuesta
            byte[] buffer = new byte[65535];

            DatagramPacket paqueteRespuesta = new DatagramPacket(
                    buffer,
                    buffer.length
            );

            System.out.println("[CLIENTE-UDP] Esperando respuesta...");

            socket.receive(paqueteRespuesta);

            String jsonRespuesta = new String(
                    paqueteRespuesta.getData(),
                    0,
                    paqueteRespuesta.getLength(),
                    StandardCharsets.UTF_8
            );

            System.out.println("[CLIENTE-UDP] Respuesta recibida:");
            System.out.println(jsonRespuesta);

            // 🔥 Deserializar respuesta
            Respuesta<?> respuesta =
                    JsonUtil.fromJson(jsonRespuesta, Respuesta.class);

            System.out.println("[CLIENTE-UDP] Estado: " + respuesta.getEstado());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}