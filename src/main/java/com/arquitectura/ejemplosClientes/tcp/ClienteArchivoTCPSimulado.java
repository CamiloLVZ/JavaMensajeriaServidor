package com.arquitectura.ejemplosClientes.tcp;

import com.arquitectura.infraestructura.serializacion.JsonUtil;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Protocolo;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadEnviarArchivo;

import javax.swing.JFileChooser;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

public class ClienteArchivoTCPSimulado {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_USERNAME = "cliente-archivo-tcp";

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int puerto = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
        String username = args.length > 2 ? args[2] : DEFAULT_USERNAME;

        try {
            Path archivoSeleccionado = seleccionarArchivo();
            if (archivoSeleccionado == null) {
                System.out.println("No se selecciono ningun archivo. Envio cancelado.");
                return;
            }

            enviarConexion(host, puerto, username);
            enviarArchivo(host, puerto, username, archivoSeleccionado);
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

    private static void enviarArchivo(String host, int puerto, String username, Path archivoSeleccionado) throws Exception {
        try (Socket socket = new Socket(host, puerto)) {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            Mensaje<PayloadEnviarArchivo> mensaje = new Mensaje<>();
            mensaje.setTipo(TipoMensaje.REQUEST);
            mensaje.setAccion(Accion.ENVIAR_DOCUMENTO);

            Metadata metadata = new Metadata();
            metadata.setIdMensaje(UUID.randomUUID().toString());
            metadata.setClientId(username);
            metadata.setTimestamp(LocalDateTime.now());
            metadata.setProtocolo(Protocolo.TCP);
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
