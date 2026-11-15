import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/** El servicio de pedidos de una tienda. Lleva tiempo en producción. No tiene pruebas. */
public class Pedidos {

    static final Map<String, Long> PRECIOS = Map.of("teclado", 15_000_000L, "raton", 6_000_000L, "monitor", 90_000_000L);
    static final Map<String, Integer> EXISTENCIAS = new HashMap<>(Map.of("teclado", 10, "raton", 3, "monitor", 2));
    static int secuencia = 0;

    public static void main(String[] args) throws Exception {
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/pedidos", Pedidos::crearPedido);
        servidor.start();
        int puerto = servidor.getAddress().getPort();
        try {
            System.out.println(enviar(puerto, "cliente=cliente-1&linea=teclado:2&linea=raton:1"));
            System.out.println(enviar(puerto, "cliente=cliente-2&linea=raton:5"));
            System.out.println(enviar(puerto, "cliente=&linea=raton:1"));
        } finally {
            servidor.stop(0);
        }
    }

    static void crearPedido(HttpExchange peticion) throws IOException {
        String cuerpo = new String(peticion.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String cliente = "";
        long total = 0;
        StringBuilder lineas = new StringBuilder();
        int cantidadDeLineas = 0;
        for (String par : cuerpo.split("&")) {
            String[] claveValor = par.split("=", 2);
            if (claveValor[0].equals("cliente")) {
                cliente = claveValor.length > 1 ? claveValor[1] : "";
            }
            if (claveValor[0].equals("linea")) {
                String[] partes = claveValor[1].split(":");
                Long precio = PRECIOS.get(partes[0]);
                if (precio == null) {
                    responder(peticion, 422, "{\"error\":\"producto desconocido\"}");
                    return;
                }
                int cantidad = Integer.parseInt(partes[1]);
                if (cantidad < 1) {
                    responder(peticion, 422, "{\"error\":\"cantidad inválida\"}");
                    return;
                }
                if (EXISTENCIAS.get(partes[0]) < cantidad) {
                    responder(peticion, 422, "{\"error\":\"sin existencias\"}");
                    return;
                }
                EXISTENCIAS.merge(partes[0], -cantidad, Integer::sum);
                total += precio * cantidad;
                lineas.append(partes[0]).append(":").append(cantidad).append(":").append(precio).append(",");
                cantidadDeLineas++;
            }
        }
        if (cliente.isBlank()) {
            responder(peticion, 422, "{\"error\":\"falta el cliente\"}");
            return;
        }
        if (cantidadDeLineas == 0) {
            responder(peticion, 422, "{\"error\":\"pedido sin líneas\"}");
            return;
        }
        if (total > 500_000_000L) {
            responder(peticion, 422, "{\"error\":\"supera el tope\"}");
            return;
        }
        String id = "pedido-" + (++secuencia);
        Path archivo = Path.of(System.getProperty("java.io.tmpdir"), "pedidos.txt");
        Files.writeString(archivo, id + "|" + cliente + "|PENDIENTE|" + LocalDateTime.now() + "|" + lineas + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        System.out.println("  [correo a " + cliente + "] recibimos tu pedido " + id);
        responder(peticion, 201, "{\"id\":\"" + id + "\",\"totalCentavos\":" + total + "}");
    }

    static void responder(HttpExchange peticion, int estado, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        peticion.sendResponseHeaders(estado, bytes.length);
        peticion.getResponseBody().write(bytes);
        peticion.close();
    }

    static String enviar(int puerto, String cuerpo) throws IOException, InterruptedException {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + "/pedidos"))
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build();
        HttpResponse<String> respuesta = HttpClient.newHttpClient().send(peticion, HttpResponse.BodyHandlers.ofString());
        return respuesta.statusCode() + " " + respuesta.body();
    }
}
