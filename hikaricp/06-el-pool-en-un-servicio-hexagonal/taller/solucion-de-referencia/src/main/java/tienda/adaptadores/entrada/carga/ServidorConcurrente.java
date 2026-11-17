package tienda.adaptadores.entrada.carga;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.aplicacion.puertos.entrada.CrearPedido;
import tienda.aplicacion.puertos.entrada.CrearPedido.Linea;
import tienda.aplicacion.puertos.entrada.CrearPedido.Solicitud;
import tienda.dominio.ReglaDeNegocioViolada;

/**
 * Un adaptador de entrada HTTP que atiende cada petición en su propio hilo virtual. El ServidorHttp del módulo hexagonal
 * atiende de una en una (no le da ejecutor a HttpServer): con él, la carga nunca llegaría al pool.
 *   POST /pedidos   cliente, producto, cantidad, cabecera Idempotency-Key  -> id del pedido (200), 422, 409 o 503
 *   GET  /pool      el estado del pool en JSON (lo da la raíz de composición)
 */
public final class ServidorConcurrente {

    private final HttpServer servidor;

    public ServidorConcurrente(int puerto, CrearPedido crear, Supplier<String> pool) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", puerto), 2000);
        servidor.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        servidor.createContext("/pedidos", intercambio -> {
            Map<String, String> datos = formulario(intercambio);
            String clave = intercambio.getRequestHeaders().getFirst("Idempotency-Key");
            atender(intercambio, () -> crear.crear(new Solicitud(clave, datos.get("cliente"),
                    List.of(new Linea(datos.get("producto"), Integer.parseInt(datos.get("cantidad")))))).pedido().id());
        });
        servidor.createContext("/pool", intercambio -> atender(intercambio, pool));
    }

    public void iniciar() {
        servidor.start();
    }

    private static void atender(HttpExchange intercambio, Supplier<String> trabajo) throws IOException {
        int estado;
        String cuerpo;
        try {
            cuerpo = trabajo.get();
            estado = 200;
        } catch (ReglaDeNegocioViolada rechazo) {
            estado = 422;
            cuerpo = rechazo.getMessage();
        } catch (ConflictoDeConcurrencia conflicto) {
            estado = 409;
            cuerpo = conflicto.getMessage();
        } catch (RuntimeException fallo) {
            estado = 503;                                        // sin conexión a tiempo, o la base caída
            cuerpo = String.valueOf(fallo.getMessage());
        }
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        try (intercambio; OutputStream salida = intercambio.getResponseBody()) {
            intercambio.sendResponseHeaders(estado, bytes.length);
            salida.write(bytes);
        }
    }

    private static Map<String, String> formulario(HttpExchange intercambio) throws IOException {
        String texto = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> datos = new HashMap<>();
        for (String par : texto.split("&")) {
            int igual = par.indexOf('=');
            if (igual > 0) {
                datos.put(par.substring(0, igual), URLDecoder.decode(par.substring(igual + 1), StandardCharsets.UTF_8));
            }
        }
        return datos;
    }
}
