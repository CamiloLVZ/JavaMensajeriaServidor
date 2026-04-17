package com.arquitectura.ejemplosClientes.udp;

import com.arquitectura.infraestructura.serializacion.JsonUtil;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Protocolo;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadEnviarArchivo;

import javax.swing.JFileChooser;
import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

public class ClienteArchivoUDPSimulado {

    public static void main(String[] args) {

        String host = "localhost";
        int puertoServidor = 8080;

        try (DatagramSocket socket = new DatagramSocket()) {

            Mensaje<PayloadEnviarArchivo> mensaje = new Mensaje<>();
            mensaje.setTipo(TipoMensaje.REQUEST);
            mensaje.setAccion(Accion.ENVIAR_DOCUMENTO);

            Metadata metadata = new Metadata();
            metadata.setIdMensaje(UUID.randomUUID().toString());
            metadata.setClientId("cliente-archivo-udp");
            metadata.setTimestamp(LocalDateTime.now());
            metadata.setProtocolo(Protocolo.UDP);
            mensaje.setMetadata(metadata);

            PayloadEnviarArchivo payload = new PayloadEnviarArchivo();
            Path archivoSeleccionado = seleccionarArchivo();
            if (archivoSeleccionado == null) {
                System.out.println("No se selecciono ningun archivo. Envio cancelado.");
                return;
            }

            byte[] contenidoBytes = Files.readAllBytes(archivoSeleccionado);
            payload.setNombre(extraerNombreBase(archivoSeleccionado.getFileName().toString()));
            payload.setExtension(extraerExtension(archivoSeleccionado.getFileName().toString()));
            payload.setContenido(Base64.getEncoder().encodeToString(contenidoBytes));
            payload.setTamano(contenidoBytes.length);
            payload.setClientIdDestino("servidor");
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

    private static Path seleccionarArchivo() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Selecciona un archivo para enviar");
        int resultado = fileChooser.showOpenDialog(null);
        if (resultado != JFileChooser.APPROVE_OPTION) {
            return null;
        }

        File archivo = fileChooser.getSelectedFile();
        if (archivo == null) {
            return null;
        }

        return archivo.toPath();
    }

    private static String extraerNombreBase(String nombreArchivoCompleto) {
        int ultimoPunto = nombreArchivoCompleto.lastIndexOf('.');
        if (ultimoPunto <= 0) {
            return nombreArchivoCompleto;
        }

        return nombreArchivoCompleto.substring(0, ultimoPunto);
    }

    private static String extraerExtension(String nombreArchivoCompleto) {
        int ultimoPunto = nombreArchivoCompleto.lastIndexOf('.');
        if (ultimoPunto <= 0 || ultimoPunto == nombreArchivoCompleto.length() - 1) {
            return "";
        }

        return nombreArchivoCompleto.substring(ultimoPunto + 1);
    }
}
