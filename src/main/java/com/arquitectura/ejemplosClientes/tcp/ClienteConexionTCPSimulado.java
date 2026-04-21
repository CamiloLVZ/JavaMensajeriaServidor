package com.arquitectura.ejemplosClientes.tcp;

import com.arquitectura.infraestructura.serializacion.JsonUtil;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Protocolo;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadConectar;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

public class ClienteConexionTCPSimulado {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_USERNAME = "cliente-tcp-demo";

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int puerto = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
        String username = args.length > 2 ? args[2] : DEFAULT_USERNAME;

        try (Socket socket = new Socket(host, puerto)) {
            System.out.println("[CLIENTE-TCP] Conectado a " + host + ":" + puerto);

            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            String json = JsonUtil.toJson(crearMensajeConexion(username));
            System.out.println("[CLIENTE-TCP] Enviando registro para " + username);
            System.out.println(json);

            writer.write(json);
            writer.newLine();
            writer.flush();

            String jsonRespuesta = reader.readLine();
            Respuesta<?> respuesta = JsonUtil.fromJson(jsonRespuesta, Respuesta.class);

            System.out.println("[CLIENTE-TCP] Respuesta:");
            System.out.println(jsonRespuesta);
            System.out.println("[CLIENTE-TCP] Estado: " + respuesta.getEstado());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Mensaje<PayloadConectar> crearMensajeConexion(String username) {
        Mensaje<PayloadConectar> mensaje = new Mensaje<>();
        mensaje.setTipo(TipoMensaje.REQUEST);
        mensaje.setAccion(Accion.CONECTAR);

        Metadata metadata = new Metadata();
        metadata.setIdMensaje(UUID.randomUUID().toString());
        metadata.setTimestamp(LocalDateTime.now());
        metadata.setClientId(username);
        metadata.setProtocolo(Protocolo.TCP);
        mensaje.setMetadata(metadata);

        PayloadConectar payload = new PayloadConectar();
        payload.setUsername(username);
        mensaje.setPayload(payload);
        return mensaje;
    }
}
