package com.arquitectura;

import com.arquitectura.infraestructura.JsonUtil;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Protocolo;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadConectar;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.UUID;

public class ClienteSimulado {

    public static void main(String[] args) throws IOException {
        String protocolo = "TCP";
        String host = "localhost";
        int puerto = 8080;
        Socket socket = new Socket(host, puerto);

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

        OutputStream out = socket.getOutputStream();
        out.write(json.getBytes());
        out.flush();


        InputStream in = socket.getInputStream();
        byte[] buffer = new byte[4096];
        int bytesRead = in.read(buffer);

        String respuesta = new String(buffer, 0, bytesRead);

        System.out.println(respuesta);

        socket.close();
    }

}
