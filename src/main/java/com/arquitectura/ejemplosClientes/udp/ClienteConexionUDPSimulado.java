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

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_USERNAME = "cliente-udp-demo";

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int puertoServidor = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
        String username = args.length > 2 ? args[2] : DEFAULT_USERNAME;

        try (DatagramSocket socket = new DatagramSocket()) {
            String jsonRespuesta = enviarConexion(socket, host, puertoServidor, username);
            Respuesta<?> respuesta = JsonUtil.fromJson(jsonRespuesta, Respuesta.class);

            System.out.println("[CLIENTE-UDP] Respuesta:");
            System.out.println(jsonRespuesta);
            System.out.println("[CLIENTE-UDP] Estado: " + respuesta.getEstado());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String enviarConexion(DatagramSocket socket, String host, int puertoServidor, String username) throws Exception {
        String json = JsonUtil.toJson(crearMensajeConexion(username));
        System.out.println("[CLIENTE-UDP] Enviando registro para " + username);
        System.out.println(json);

        InetAddress address = InetAddress.getByName(host);
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        DatagramPacket paqueteEnvio = new DatagramPacket(data, data.length, address, puertoServidor);
        socket.send(paqueteEnvio);

        byte[] buffer = new byte[65535];
        DatagramPacket paqueteRespuesta = new DatagramPacket(buffer, buffer.length);
        socket.receive(paqueteRespuesta);

        return new String(paqueteRespuesta.getData(), 0, paqueteRespuesta.getLength(), StandardCharsets.UTF_8);
    }

    public static Mensaje<PayloadConectar> crearMensajeConexion(String username) {
        Mensaje<PayloadConectar> mensaje = new Mensaje<>();
        mensaje.setTipo(TipoMensaje.REQUEST);
        mensaje.setAccion(Accion.CONECTAR);

        Metadata metadata = new Metadata();
        metadata.setIdMensaje(UUID.randomUUID().toString());
        metadata.setTimestamp(LocalDateTime.now());
        metadata.setClientId(username);
        metadata.setProtocolo(Protocolo.UDP);
        mensaje.setMetadata(metadata);

        PayloadConectar payload = new PayloadConectar();
        payload.setUsername(username);
        mensaje.setPayload(payload);
        return mensaje;
    }
}
