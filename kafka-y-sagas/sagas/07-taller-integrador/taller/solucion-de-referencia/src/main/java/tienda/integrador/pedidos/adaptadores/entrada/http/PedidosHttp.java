package tienda.integrador.pedidos.adaptadores.entrada.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import tienda.integrador.pedidos.aplicacion.Puertos.AceptarPedido;
import tienda.integrador.pedidos.aplicacion.Puertos.AceptarPedido.Solicitud;
import tienda.integrador.pedidos.aplicacion.Puertos.ConsultarPedido;
import tienda.integrador.pedidos.dominio.Pedido;
import tienda.integrador.plataforma.PublicadorDeSalida;

/**
 * Adaptador de entrada HTTP: POST /pedidos?... (201 PENDIENTE), GET /pedidos/{id}, y operación: /admin/publicador/detener|iniciar y /metricas.
 * Solo traduce a los puertos de entrada y sus resultados a JSON.
 */
public final class PedidosHttp implements AutoCloseable {

    private final HttpServer servidor;

    public PedidosHttp(AceptarPedido aceptar, ConsultarPedido consultar, PublicadorDeSalida salida, String host, int puerto) throws IOException {
        if (puerto == 0) {
            servidor = null;
            return;
        }
        servidor = HttpServer.create(new InetSocketAddress(host, puerto), 0);
        servidor.createContext("/pedidos", ex -> {
            if (ex.getRequestMethod().equals("POST")) crear(ex, aceptar); else consultar(ex, consultar);
        });
        servidor.createContext("/admin/publicador/detener", ex -> { salida.activa = false; responder(ex, 200, "publicador detenido"); });
        servidor.createContext("/admin/publicador/iniciar", ex -> { salida.activa = true; responder(ex, 200, "publicador en marcha"); });
        servidor.createContext("/metricas", ex -> responder(ex, 200, "filas_pendientes_salida{servicio=\"pedidos\"} " + salida.pendientes() + "\n"
                + "pedidos_pendientes " + consultar.pendientes() + "\n"));
        servidor.start();
    }

    private static void crear(HttpExchange ex, AceptarPedido aceptar) throws IOException {
        try {
            Map<String, String> q = consulta(ex.getRequestURI().getRawQuery());
            String id = q.getOrDefault("id", "p-" + UUID.randomUUID());
            String correlacion = ex.getRequestHeaders().getFirst("X-Correlacion") != null ? ex.getRequestHeaders().getFirst("X-Correlacion") : id;
            Pedido p = aceptar.aceptar(new Solicitud(id, correlacion, q.get("producto"), Integer.parseInt(q.get("cantidad")), Long.parseLong(q.get("centavos")), q.get("ciudad")));
            responder(ex, 201, "{\"id\":\"" + p.id() + "\",\"estado\":\"" + p.estado() + "\",\"correlacion\":\"" + p.correlacion() + "\"}");
        } catch (RuntimeException e) {
            responder(ex, 400, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private static void consultar(HttpExchange ex, ConsultarPedido consultar) throws IOException {
        String id = ex.getRequestURI().getPath().substring("/pedidos/".length());
        consultar.porId(id).ifPresentOrElse(p -> {
            try {
                responder(ex, 200, "{\"id\":\"" + id + "\",\"estado\":\"" + p.estado() + "\",\"motivo\":" + (p.motivo() == null ? "null" : "\"" + p.motivo() + "\"") + "}");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }, () -> {
            try {
                responder(ex, 404, "{}");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static Map<String, String> consulta(String q) {
        Map<String, String> m = new HashMap<>();
        if (q == null) return m;
        for (String par : q.split("&")) {
            String[] kv = par.split("=", 2);
            m.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return m;
    }

    private static void responder(HttpExchange ex, int codigo, String texto) throws IOException {
        byte[] cuerpo = texto.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(codigo, cuerpo.length);
        ex.getResponseBody().write(cuerpo);
        ex.close();
    }

    @Override
    public void close() {
        if (servidor != null) servidor.stop(0);
    }
}
