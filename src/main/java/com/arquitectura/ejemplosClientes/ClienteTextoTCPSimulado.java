package com.arquitectura.ejemplosClientes;

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

    public static void main(String[] args) {

        String host = "localhost";
        int puerto = 8080;

        try (Socket socket = new Socket(host, puerto)) {

            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            Mensaje<Map<String, String>> mensaje = new Mensaje<>();
            mensaje.setTipo(TipoMensaje.REQUEST);
            mensaje.setAccion(Accion.ENVIAR_MENSAJE);

            Metadata metadata = new Metadata();
            metadata.setIdMensaje(UUID.randomUUID().toString());
            metadata.setClientId("cliente-a");
            metadata.setTimestamp(LocalDateTime.now());
            metadata.setProtocolo(Protocolo.TCP);
            mensaje.setMetadata(metadata);

            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("autor", "cliente-a");
            payload.put("contenido", "Hola servidor, este es un mensaje publicado.");
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

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
