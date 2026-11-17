package tienda.integrador;

import comun.Kf;
import tienda.integrador.verificador.Invariantes;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** Siete servicios como procesos: Pedidos, Orquestador, Inventario, Pagos, Envíos, Notificaciones y Fidelidad. */
final class Mundo7 implements AutoCloseable {

    static final String B = Kf.bootstrap();
    static final List<String> SERVICIOS = List.of("pedidos", "orquestador", "inventario", "pagos", "envios", "notificaciones", "fidelidad");
    static final List<String> GRUPOS = List.of("pedidos", "orquestador", "orquestador-pedidos", "inventario", "pagos", "envios", "notificaciones", "fidelidad");
    static final List<String> TEMAS = List.of(Temas.PEDIDOS, Temas.ORDENES_INVENTARIO, Temas.ORDENES_PAGOS, Temas.ORDENES_ENVIOS, Temas.RESPUESTAS, Temas.RESULTADOS, Temas.FALLIDOS);
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(2)).build();

    final Map<String, List<Process>> procesos = new HashMap<>();
    final Map<String, List<StringBuffer>> salidas = new HashMap<>();
    final Map<String, JdbcTemplate> bd = new HashMap<>();
    final long existencias;
    final List<SingleConnectionDataSource> fuentes = new ArrayList<>();

    Mundo7(long libro, int unidadesLimitadas) throws Exception {
        try (Admin admin = Kf.admin(B)) {
            for (int i = 0; i < 30; i++) {
                try {
                    admin.deleteConsumerGroups(GRUPOS).all().get();
                    break;
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("GroupIdNotFound")) break;
                    Thread.sleep(500);
                }
            }
        }
        for (String t : TEMAS) Kf.tema(B, t, 3, 1, Map.of());
        try (Connection c = DriverManager.getConnection(Kf.pgUrl(), "taller", "taller"); Statement st = c.createStatement()) {
            for (String s : SERVICIOS) {
                st.execute("DROP SCHEMA IF EXISTS s07_" + s + " CASCADE");
                st.execute("CREATE SCHEMA s07_" + s);
            }
        }
        // UNA conexión por base (no una por consulta): el verificador hace cientos de consultas y agotaba los puertos efímeros del anfitrión
        for (String s : SERVICIOS) {
            var ds = new SingleConnectionDataSource(Kf.pgUrl() + "?currentSchema=s07_" + s, "taller", "taller", true);
            fuentes.add(ds);
            bd.put(s, new JdbcTemplate(ds));
        }
        // tablas de inventario antes de arrancar, para sembrar existencias
        var inv = bd.get("inventario");
        inv.execute("CREATE TABLE IF NOT EXISTS stock(producto text PRIMARY KEY, disponibles int NOT NULL CHECK (disponibles >= 0))");
        inv.update("INSERT INTO stock VALUES ('libro', ?), ('raro', 0)", libro);
        for (int k = 0; k < unidadesLimitadas; k++) inv.update("INSERT INTO stock VALUES (?, 5)", "lim-" + k);
        existencias = libro + 5L * unidadesLimitadas;
    }

    Process arrancar(String servicio, String... extra) {
        List<String> args = new ArrayList<>(List.of("--tienda.servicio=" + servicio, "--logging.level.tienda=info"));
        int n = procesos.getOrDefault(servicio, List.of()).size();
        if (servicio.equals("pedidos")) args.add("--tienda.puerto=" + (47601 + n));
        if (servicio.equals("orquestador")) args.add("--tienda.puerto=" + (47611 + n));
        args.addAll(List.of(extra));
        Process p = Kf.hijo(Integrador.class, args.toArray(String[]::new));
        procesos.computeIfAbsent(servicio, k -> new ArrayList<>()).add(p);
        salidas.computeIfAbsent(servicio, k -> new ArrayList<>()).add(Kf.recoger(p));
        return p;
    }

    void arrancarTodos(String... extra) {
        for (String s : SERVICIOS) arrancar(s, extra);
        esperarListos(1);
    }

    void esperarListos(int instancias) {
        for (String g : List.of("orquestador", "orquestador-pedidos", "inventario", "pagos", "envios", "notificaciones", "fidelidad", "pedidos")) esperarGrupo(g, instancias);
        Kf.esperar(() -> codigo("GET", 47601, "/metricas", null) == 200, 60_000, "HTTP de Pedidos");
    }

    static void esperarGrupo(String grupo, int miembros) {
        Kf.esperar(() -> {
            try (Admin a = Kf.admin(B)) {
                return a.describeConsumerGroups(List.of(grupo)).all().get().get(grupo).members().size() >= miembros;
            }
        }, 360_000, "grupo " + grupo + " con " + miembros + " miembro(s)");
    }

    /** Mata (kill -9) todas las instancias de un servicio. */
    void matar(String servicio) throws InterruptedException {
        for (Process p : procesos.getOrDefault(servicio, List.of())) {
            p.destroyForcibly();
            p.waitFor(20, TimeUnit.SECONDS);
        }
        procesos.put(servicio, new ArrayList<>());
    }

    static int codigo(String metodo, int puerto, String ruta, String correlacion) {
        try {
            var b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta)).timeout(java.time.Duration.ofSeconds(5))
                    .method(metodo, HttpRequest.BodyPublishers.noBody());
            if (correlacion != null) b.header("X-Correlacion", correlacion);
            return HTTP.send(b.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (Exception e) {
            return -1;
        }
    }

    static String cuerpo(String metodo, int puerto, String ruta) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta)).method(metodo, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    /** Un pedido de uno de los cuatro caminos. Devuelve el código HTTP. */
    static int pedir(int puerto, String id, String camino) {
        String q = switch (camino) {
            case "feliz" -> "producto=libro&cantidad=1&centavos=1000&ciudad=Quito";
            case "pago-rechazado" -> "producto=libro&cantidad=1&centavos=666&ciudad=Quito";
            case "sin-existencias" -> "producto=raro&cantidad=1&centavos=1000&ciudad=Quito";
            case "envio-imposible" -> "producto=libro&cantidad=1&centavos=1000&ciudad=Atlantis";
            default -> "producto=" + camino + "&cantidad=1&centavos=1000&ciudad=Quito";         // un producto concreto (venta de unidades limitadas)
        };
        return codigo("POST", puerto, "/pedidos?id=" + id + "&" + q, null);
    }

    long n(String servicio, String sql) {
        Long v = bd.get(servicio).queryForObject(sql, Long.class);
        return v == null ? 0 : v;
    }

    void esperarTerminados(long maxMs) {
        Kf.esperar(() -> n("pedidos", "SELECT count(*) FROM pedidos WHERE estado = 'PENDIENTE'") == 0, maxMs, "todos los pedidos terminados");
    }

    List<String> verificar() {
        return Invariantes.verificar(bd, existencias, 600);
    }

    @Override
    public void close() throws InterruptedException {
        fuentes.forEach(SingleConnectionDataSource::destroy);
        for (List<Process> l : procesos.values()) for (Process p : l) if (p.isAlive()) { p.destroyForcibly(); p.waitFor(20, TimeUnit.SECONDS); }
    }
}
