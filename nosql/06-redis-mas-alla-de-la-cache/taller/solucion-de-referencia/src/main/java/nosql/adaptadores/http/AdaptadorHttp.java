package nosql.adaptadores.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import nosql.aplicacion.ConsultarProducto;
import nosql.aplicacion.ErrorDeAlmacenamiento;
import nosql.aplicacion.LimitadorDeTasa;
import nosql.aplicacion.ListarPedidosPaginados;
import nosql.aplicacion.RegistroDeIdempotencia;
import org.bson.Document;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.aplicacion.PedidoNoEncontrado;
import tienda.aplicacion.VistaDePedido;
import tienda.aplicacion.puertos.entrada.AprobarPedido;
import tienda.aplicacion.puertos.entrada.CancelarPedido;
import tienda.aplicacion.puertos.entrada.ConfirmarPedido;
import tienda.aplicacion.puertos.entrada.ConsultarPedido;
import tienda.aplicacion.puertos.entrada.CrearPedido;
import tienda.dominio.ReglaDeNegocioViolada;

/**
 * El adaptador HTTP del servicio de pedidos (com.sun.net.httpserver, sin Spring). Interpreta HTTP y JSON y llama a puertos de entrada.
 * En el borde aplica el límite de tasa y la idempotencia por {@code Idempotency-Key}, ambos detrás de puertos: no sabe que hay Redis.
 * Ningún error técnico sale como texto de excepción: un fallo del almacén es un 503 genérico, y el resto un 500 genérico.
 */
public final class AdaptadorHttp implements AutoCloseable {

    private final HttpServer servidor;
    private final CrearPedido crear;
    private final AprobarPedido aprobar;
    private final ConfirmarPedido confirmar;
    private final CancelarPedido cancelar;
    private final ConsultarPedido consultar;
    private final ListarPedidosPaginados historial;
    private final ConsultarProducto productos;
    private final LimitadorDeTasa limitador;
    private final RegistroDeIdempotencia idempotencia;
    private final Supplier<Map<String, Object>> metricasExtra;

    public final AtomicLong limitadas = new AtomicLong(), conflictos = new AtomicLong(), rechazosDeNegocio = new AtomicLong(), creados = new AtomicLong();

    public AdaptadorHttp(int puerto, CrearPedido crear, AprobarPedido aprobar, ConfirmarPedido confirmar, CancelarPedido cancelar, ConsultarPedido consultar,
                         ListarPedidosPaginados historial, ConsultarProducto productos, LimitadorDeTasa limitador, RegistroDeIdempotencia idempotencia,
                         Supplier<Map<String, Object>> metricasExtra) {
        this.crear = crear; this.aprobar = aprobar; this.confirmar = confirmar; this.cancelar = cancelar; this.consultar = consultar;
        this.historial = historial; this.productos = productos; this.limitador = limitador; this.idempotencia = idempotencia; this.metricasExtra = metricasExtra;
        try { servidor = HttpServer.create(new InetSocketAddress(puerto), 0); } catch (IOException e) { throw new IllegalStateException(e); }
        servidor.setExecutor(Executors.newFixedThreadPool(64));
        servidor.createContext("/", this::despachar);
        servidor.start();
    }

    public int puerto() { return servidor.getAddress().getPort(); }

    @Override public void close() { servidor.stop(0); }

    private void despachar(HttpExchange x) throws IOException {
        try {
            String metodo = x.getRequestMethod(), ruta = x.getRequestURI().getPath();
            String[] p = ruta.split("/");
            if (metodo.equals("POST") && ruta.equals("/pedidos")) { crearPedido(x); return; }
            if (metodo.equals("GET") && ruta.equals("/metricas")) { responder(x, 200, new Document(metricas()).toJson()); return; }
            if (metodo.equals("GET") && p.length == 3 && p[1].equals("pedidos")) {
                var v = consultar.porId(p[2]);
                if (v.isEmpty()) responder(x, 404, "{\"error\":\"pedido no encontrado\"}"); else responder(x, 200, aJson(v.get()));
                return;
            }
            if (metodo.equals("POST") && p.length == 4 && p[1].equals("pedidos")) {
                VistaDePedido r = switch (p[3]) {
                    case "aprobar" -> aprobar.aprobar(p[2]);
                    case "confirmar", "pagar" -> confirmar.confirmar(p[2]);
                    case "cancelar" -> cancelar.cancelar(p[2]);
                    default -> null;
                };
                if (r == null) responder(x, 404, "{}"); else responder(x, 200, aJson(r));
                return;
            }
            if (metodo.equals("GET") && p.length == 3 && p[1].equals("productos")) {
                var prod = productos.porId(p[2]);
                if (prod.isEmpty()) responder(x, 404, "{\"error\":\"producto no encontrado\"}");
                else responder(x, 200, new Document("id", p[2]).append("precioCentavos", prod.get().precioCentavos()).append("existencias", prod.get().existencias()).toJson());
                return;
            }
            if (metodo.equals("GET") && p.length == 4 && p[1].equals("clientes") && p[3].equals("pedidos")) {
                String despues = null; int n = 20;
                String q = x.getRequestURI().getQuery();
                if (q != null) for (String par : q.split("&")) {
                    String[] kv = par.split("=", 2);
                    if (kv[0].equals("despues")) despues = java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                    if (kv[0].equals("n")) n = Integer.parseInt(kv[1]);
                }
                var pagina = historial.pagina(p[2], despues, n);
                Document d = new Document("pedidos", pagina.pedidos().stream().map(AdaptadorHttp::aDoc).toList());
                if (pagina.siguiente() != null) d.append("siguiente", pagina.siguiente());
                responder(x, 200, d.toJson());
                return;
            }
            responder(x, 404, "{}");
        } catch (PedidoNoEncontrado e) {
            responder(x, 404, "{\"error\":\"pedido no encontrado\"}");
        } catch (ReglaDeNegocioViolada e) {
            rechazosDeNegocio.incrementAndGet();
            responder(x, 422, new Document("error", e.getMessage()).toJson());
        } catch (ConflictoDeConcurrencia e) {
            conflictos.incrementAndGet();
            responder(x, 409, "{\"error\":\"conflicto, reintente\"}");
        } catch (ErrorDeAlmacenamiento e) {
            responder(x, 503, "{\"error\":\"servicio no disponible, reintente\"}");
        } catch (IllegalArgumentException e) {
            responder(x, 400, "{\"error\":\"petición mal formada\"}");
        } catch (RuntimeException e) {
            responder(x, 500, "{\"error\":\"error interno\"}");
        }
    }

    private void crearPedido(HttpExchange x) throws IOException {
        String cuerpo = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String cliente;
        List<CrearPedido.Linea> lineas = new ArrayList<>();
        try {
            Document d = Document.parse(cuerpo);
            cliente = d.getString("cliente");
            for (Document l : d.getList("lineas", Document.class)) lineas.add(new CrearPedido.Linea(l.getString("producto"), l.getInteger("cantidad")));
            if (cliente == null || cliente.isBlank()) throw new IllegalArgumentException("falta el cliente");
        } catch (RuntimeException e) {
            responder(x, 400, "{\"error\":\"petición mal formada\"}");
            return;
        }
        var decision = limitador.intentar(cliente);
        if (!decision.permitido()) {
            limitadas.incrementAndGet();
            if (decision.noDisponible()) { responder(x, 503, "{\"error\":\"limitador no disponible\"}"); return; }
            x.getResponseHeaders().add("Retry-After", String.valueOf(Math.max(1, (decision.reintentarEnMs() + 999) / 1000)));
            responder(x, 429, "{\"error\":\"demasiadas peticiones\"}");
            return;
        }
        String clave = x.getRequestHeaders().getFirst("Idempotency-Key"), huella = huella(cuerpo);
        boolean conClave = clave != null && idempotencia != null;
        if (conClave) {
            var r = idempotencia.iniciar(clave, huella);
            switch (r.tipo()) {
                case CUERPO_DISTINTO -> { responder(x, 422, "{\"error\":\"la clave de idempotencia ya se usó con otro cuerpo\"}"); return; }
                case EN_CURSO -> { responder(x, 409, "{\"error\":\"en_curso\"}"); return; }
                case REPETIDA -> { responder(x, r.status(), r.cuerpo()); return; }
                case NUEVA -> { }
            }
        }
        try {
            var resultado = crear.crear(new CrearPedido.Solicitud(clave != null ? clave : "sin-clave-" + UUID.randomUUID(), cliente, lineas));
            String json = aJson(resultado.pedido());
            int status = resultado.nuevo() ? 201 : 200;
            if (resultado.nuevo()) creados.incrementAndGet();
            if (conClave) idempotencia.completar(clave, huella, status, json);
            responder(x, status, json);
        } catch (RuntimeException e) {
            if (conClave) idempotencia.abandonar(clave);
            throw e;
        }
    }

    public Map<String, Object> metricas() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("creados", creados.get());
        m.put("peticionesLimitadas", limitadas.get());
        m.put("conflictosAbandonados", conflictos.get());
        m.put("rechazosDeNegocio", rechazosDeNegocio.get());
        if (metricasExtra != null) m.putAll(metricasExtra.get());
        return m;
    }

    static Document aDoc(VistaDePedido v) {
        return new Document("id", v.id()).append("cliente", v.cliente()).append("estado", v.estado()).append("totalCentavos", v.totalCentavos())
                .append("lineas", v.lineas().stream().map(l -> new Document("producto", l.producto()).append("cantidad", l.cantidad()).append("precioUnitarioCentavos", l.precioUnitarioCentavos())).toList());
    }

    static String aJson(VistaDePedido v) { return aDoc(v).toJson(); }

    static String huella(String s) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))).substring(0, 16); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static void responder(HttpExchange x, int status, String cuerpo) throws IOException {
        byte[] b = cuerpo.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().add("Content-Type", "application/json");
        x.sendResponseHeaders(status, b.length);
        try (var o = x.getResponseBody()) { o.write(b); }
    }
}
