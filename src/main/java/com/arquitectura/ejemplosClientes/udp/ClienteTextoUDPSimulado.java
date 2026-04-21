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

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_USERNAME = "cliente-udp-demo";

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int puertoServidor = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
        String username = args.length > 2 ? args[2] : DEFAULT_USERNAME;
        String contenido = args.length > 3 ? args[3] : "Hola servidor, este mensaje fue enviado por UDP.";

        try (DatagramSocket socket = new DatagramSocket()) {
            String registro = ClienteConexionUDPSimulado.enviarConexion(socket, host, puertoServidor, username);
            System.out.println("[CLIENTE-UDP] Registro previo:");
            System.out.println(registro);

            Mensaje<Map<String, String>> mensaje = new Mensaje<>();
            mensaje.setTipo(TipoMensaje.REQUEST);
            mensaje.setAccion(Accion.ENVIAR_MENSAJE);

            Metadata metadata = new Metadata();
            metadata.setIdMensaje(UUID.randomUUID().toString());
            metadata.setClientId(username);
            metadata.setTimestamp(LocalDateTime.now());
            metadata.setProtocolo(Protocolo.UDP);
            mensaje.setMetadata(metadata);

            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("autor", username);
            payload.put("contenido", contenido);
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
