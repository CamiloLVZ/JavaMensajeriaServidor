package com.arquitectura;

import com.arquitectura.infraestructura.JsonUtil;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Protocolo;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadConectar;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

public class ClienteTCPSimulado {

    public static void main(String[] args) {

        String host = "localhost";
        int puerto = 8080;

        try (Socket socket = new Socket(host, puerto)) {

            System.out.println("[CLIENTE] Conectado a " + host + ":" + puerto);

            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            Mensaje<PayloadConectar> mensaje = new Mensaje<>();
            mensaje.setTipo(TipoMensaje.REQUEST);
            mensaje.setAccion(Accion.CONECTAR);

            Metadata meta = new Metadata();
            meta.setIdMensaje(UUID.randomUUID().toString());
            meta.setTimestamp(LocalDateTime.now());
            meta.setProtocolo(Protocolo.TCP);

            mensaje.setMetadata(meta);

            PayloadConectar payload = new PayloadConectar();
            payload.setUsername("juan");

            mensaje.setPayload(payload);

            String json = JsonUtil.toJson(mensaje);

            System.out.println("[CLIENTE] Enviando:");
            System.out.println(json);

            writer.write(json);
            writer.newLine();
            writer.flush();

            System.out.println("[CLIENTE] Esperando respuesta...");

            String jsonRespuesta = reader.readLine();

            System.out.println("[CLIENTE] Respuesta recibida:");
            System.out.println(jsonRespuesta);

            Respuesta<?> respuesta = JsonUtil.fromJson(jsonRespuesta, Respuesta.class);

            System.out.println("[CLIENTE] Estado: " + respuesta.getEstado());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
