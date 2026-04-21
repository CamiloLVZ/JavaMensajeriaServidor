package com.arquitectura.ejemplosClientes.tcp;

import com.arquitectura.infraestructura.serializacion.JsonUtil;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Protocolo;
import com.arquitectura.mensajeria.enums.TipoMensaje;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class ClienteTextoTCPSimulado {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_USERNAME = "cliente-tcp-demo";

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int puerto = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
        String username = args.length > 2 ? args[2] : DEFAULT_USERNAME;
        String contenido = args.length > 3 ? args[3] : "Hola servidor, este es un mensaje publicado.";

        try {
            enviarConexion(host, puerto, username);
            enviarMensaje(host, puerto, username, contenido);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void enviarConexion(String host, int puerto, String username) throws Exception {
        try (Socket socket = new Socket(host, puerto)) {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            String json = JsonUtil.toJson(ClienteConexionTCPSimulado.crearMensajeConexion(username));
            writer.write(json);
            writer.newLine();
            writer.flush();

            String jsonRespuesta = reader.readLine();
            System.out.println("[CLIENTE-TCP] Registro previo:");
            System.out.println(jsonRespuesta);
        }
    }

    private static void enviarMensaje(String host, int puerto, String username, String contenido) throws Exception {
        try (Socket socket = new Socket(host, puerto)) {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            Mensaje<Map<String, String>> mensaje = new Mensaje<>();
            mensaje.setTipo(TipoMensaje.REQUEST);
            mensaje.setAccion(Accion.ENVIAR_MENSAJE);

            Metadata metadata = new Metadata();
            metadata.setIdMensaje(UUID.randomUUID().toString());
            metadata.setClientId(username);
            metadata.setTimestamp(LocalDateTime.now());
            metadata.setProtocolo(Protocolo.TCP);
            mensaje.setMetadata(metadata);

            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("autor", username);
            payload.put("contenido", contenido);
            mensaje.setPayload(payload);

            String json = JsonUtil.toJson(mensaje);
            writer.write(json);
            writer.newLine();
            writer.flush();

            String jsonRespuesta = reader.readLine();
            Respuesta<?> respuesta = JsonUtil.fromJson(jsonRespuesta, Respuesta.class);

            System.out.println(json);
            System.out.println(jsonRespuesta);
            System.out.println("Estado: " + respuesta.getEstado());
        }
    }
}
