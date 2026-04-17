package com.arquitectura.ejemplosClientes.udp;

import com.arquitectura.infraestructura.serializacion.JsonUtil;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Protocolo;
import com.arquitectura.mensajeria.enums.TipoMensaje;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class ClienteTextoUDPSimulado {

    public static void main(String[] args) {

        String host = "localhost";
        int puertoServidor = 8080;

        try (DatagramSocket socket = new DatagramSocket()) {

            Mensaje<Map<String, String>> mensaje = new Mensaje<>();
            mensaje.setTipo(TipoMensaje.REQUEST);
            mensaje.setAccion(Accion.ENVIAR_MENSAJE);

            Metadata metadata = new Metadata();
            metadata.setIdMensaje(UUID.randomUUID().toString());
            metadata.setClientId("cliente-udp");
            metadata.setTimestamp(LocalDateTime.now());
            metadata.setProtocolo(Protocolo.UDP);
            mensaje.setMetadata(metadata);

            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("autor", "cliente-udp");
            payload.put("contenido", "Hola servidor, este mensaje fue enviado por UDP.");
            mensaje.setPayload(payload);

            String json = JsonUtil.toJson(mensaje);
            byte[] data = json.getBytes(StandardCharsets.UTF_8);

            InetAddress address = InetAddress.getByName(host);
            DatagramPacket paqueteEnvio = new DatagramPacket(data, data.length, address, puertoServidor);
            socket.send(paqueteEnvio);

            byte[] buffer = new byte[65535];
            DatagramPacket paqueteRespuesta = new DatagramPacket(buffer, buffer.length);
            socket.receive(paqueteRespuesta);

            String jsonRespuesta = new String(
                    paqueteRespuesta.getData(),
                    0,
                    paqueteRespuesta.getLength(),
                    StandardCharsets.UTF_8
            );

            Respuesta<?> respuesta = JsonUtil.fromJson(jsonRespuesta, Respuesta.class);

            System.out.println(json);
            System.out.println(jsonRespuesta);
            System.out.println("Estado: " + respuesta.getEstado());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
