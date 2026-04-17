package com.arquitectura.ejemplosClientes.tcp;

import com.arquitectura.infraestructura.serializacion.JsonUtil;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Protocolo;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadEnviarArchivo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

public class ClienteArchivoTCPSimulado {

    public static void main(String[] args) {

        String host = "localhost";
        int puerto = 8080;

        try (Socket socket = new Socket(host, puerto)) {

            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            Mensaje<PayloadEnviarArchivo> mensaje = new Mensaje<>();
            mensaje.setTipo(TipoMensaje.REQUEST);
            mensaje.setAccion(Accion.ENVIAR_DOCUMENTO);

            Metadata metadata = new Metadata();
            metadata.setIdMensaje(UUID.randomUUID().toString());
            metadata.setClientId("cliente-archivo");
            metadata.setTimestamp(LocalDateTime.now());
            metadata.setProtocolo(Protocolo.TCP);
            mensaje.setMetadata(metadata);

            PayloadEnviarArchivo payload = new PayloadEnviarArchivo();
            payload.setNombre("nota-servidor");
            payload.setExtension("txt");
            payload.setContenido("Este es un archivo de prueba enviado al servidor.");
            payload.setTamano(payload.getContenido().getBytes(StandardCharsets.UTF_8).length);
            payload.setClientIdDestino("servidor");
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
