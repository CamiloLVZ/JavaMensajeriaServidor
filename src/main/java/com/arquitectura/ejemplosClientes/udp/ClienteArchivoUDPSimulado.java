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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

public class ClienteArchivoUDPSimulado {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_USERNAME = "cliente-archivo-udp";

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int puertoServidor = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
        String username = args.length > 2 ? args[2] : DEFAULT_USERNAME;

        try (DatagramSocket socket = new DatagramSocket()) {
            Path archivoSeleccionado = seleccionarArchivo();
            if (archivoSeleccionado == null) {
                System.out.println("No se selecciono ningun archivo. Envio cancelado.");
                return;
            }

            String registro = ClienteConexionUDPSimulado.enviarConexion(socket, host, puertoServidor, username);
            System.out.println("[CLIENTE-UDP] Registro previo:");
            System.out.println(registro);

            Mensaje<PayloadEnviarArchivo> mensaje = new Mensaje<>();
            mensaje.setTipo(TipoMensaje.REQUEST);
            mensaje.setAccion(Accion.ENVIAR_DOCUMENTO);

            Metadata metadata = new Metadata();
            metadata.setIdMensaje(UUID.randomUUID().toString());
            metadata.setClientId(username);
            metadata.setTimestamp(LocalDateTime.now());
            metadata.setProtocolo(Protocolo.UDP);
            mensaje.setMetadata(metadata);

            byte[] contenidoBytes = Files.readAllBytes(archivoSeleccionado);
            PayloadEnviarArchivo payload = new PayloadEnviarArchivo();
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
        return archivo == null ? null : archivo.toPath();
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
