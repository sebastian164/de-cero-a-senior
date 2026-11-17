package tienda;

import comun.Kf;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** El entorno de las pruebas: cuatro servicios como PROCESOS (para poder matarlos de verdad), sus bases y sus temas. */
public final class Mundo implements AutoCloseable {

    public static final String B = Kf.bootstrap();
    public static final List<String> SERVICIOS = List.of("orquestador", "inventario", "pagos", "envios");
    public static final List<String> TEMAS = List.of(Mensajes.ORDENES_INVENTARIO, Mensajes.ORDENES_PAGOS, Mensajes.ORDENES_ENVIOS, Mensajes.RESPUESTAS, Mensajes.RESULTADOS, Mensajes.FALLIDOS);

    public final List<Process> procesos = new ArrayList<>();
    public final Map<String, JdbcTemplate> bases = new java.util.HashMap<>();

    public Mundo(int existencias) throws Exception {
        try (Admin admin = Kf.admin(B)) {
            for (int i = 0; i < 30; i++) {
                try {
                    admin.deleteConsumerGroups(List.of("orquestador", "inventario", "pagos", "envios")).all().get();
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
                st.execute("DROP SCHEMA IF EXISTS s05_" + s + " CASCADE");
                st.execute("CREATE SCHEMA s05_" + s);
            }
        }
        for (String s : SERVICIOS) {
            var ds = new DriverManagerDataSource(Kf.pgUrl() + "?currentSchema=s05_" + s, "taller", "taller");
            var jdbc = new JdbcTemplate(ds);
            new Configuracion.Esquema(jdbc, s);
            bases.put(s, jdbc);
        }
        bases.get("inventario").update("INSERT INTO stock VALUES ('libro', ?), ('raro', 0)", existencias);
    }

    public JdbcTemplate bd(String servicio) {
        return bases.get(servicio);
    }

    /** Un orquestador en ESTE proceso, solo para iniciar sagas y consultar (no consume nada de Kafka). */
    public Orquestador cliente() {
        var ds = new DriverManagerDataSource(Kf.pgUrl() + "?currentSchema=s05_orquestador", "taller", "taller");
        return new Orquestador(new JdbcTemplate(ds), new TransactionTemplate(new JdbcTransactionManager(ds)), 3000, 8);
    }

    /** Arranca un servicio como proceso. Argumentos extra: --propiedad=valor. */
    public Process arrancar(String servicio, String... extra) {
        List<String> args = new ArrayList<>(List.of("--tienda.servicio=" + servicio));
        args.addAll(List.of(extra));
        Process p = Kf.hijo(Aplicacion.class, args.toArray(String[]::new));
        Kf.recoger(p);
        procesos.add(p);
        return p;
    }

    public void arrancarTodos(String... extra) {
        for (String s : SERVICIOS) arrancar(s, extra);
        for (String g : SERVICIOS) esperarGrupo(g, 1);
    }

    public static void esperarGrupo(String grupo, int miembros) {
        Kf.esperar(() -> {
            try (Admin a = Kf.admin(B)) {
                return a.describeConsumerGroups(List.of(grupo)).all().get().get(grupo).members().size() >= miembros;
            }
        }, 120_000, "grupo " + grupo + " con " + miembros + " miembro(s)");
    }

    public static void matar(Process p) throws InterruptedException {
        p.destroyForcibly();
        p.waitFor(20, TimeUnit.SECONDS);
    }

    public void esperarTerminadas(long maxMs) {
        Kf.esperar(() -> bd("orquestador").queryForObject("SELECT count(*) FROM sagas WHERE estado NOT IN ('COMPLETADA','CANCELADA')", Long.class) == 0, maxMs, "todas las sagas terminadas");
    }

    public long n(String servicio, String sql) {
        Long v = bd(servicio).queryForObject(sql, Long.class);
        return v == null ? 0 : v;
    }

    /** La invariante de la tienda, leída de las cuatro bases. Devuelve las violaciones. */
    public List<String> violaciones(int existenciasIniciales) {
        List<String> v = new ArrayList<>();
        for (Map<String, Object> s : bd("orquestador").queryForList("SELECT id, estado FROM sagas")) {
            String id = (String) s.get("id");
            String estado = (String) s.get("estado");
            long cobrosVigentes = n("pagos", "SELECT count(*) FROM cobros WHERE saga_id = '" + id + "' AND NOT reembolsado");
            long cobrosTotales = n("pagos", "SELECT count(*) FROM cobros WHERE saga_id = '" + id + "'");
            long reservasVigentes = n("inventario", "SELECT count(*) FROM reservas WHERE saga_id = '" + id + "' AND NOT liberada");
            long despachos = n("envios", "SELECT count(*) FROM despachos WHERE saga_id = '" + id + "'");
            if (cobrosTotales > 1) v.add(id + ": cobro doble");
            if (estado.equals("COMPLETADA") && !(cobrosVigentes == 1 && reservasVigentes == 1 && despachos == 1)) v.add(id + ": completada sin exactamente un cobro, una reserva y un despacho");
            if (estado.equals("CANCELADA") && (cobrosVigentes != 0 || reservasVigentes != 0 || despachos != 0)) v.add(id + ": cancelada con cobro, reserva o despacho vigente");
        }
        long libres = n("inventario", "SELECT disponibles FROM stock WHERE producto = 'libro'");
        long vigentes = n("inventario", "SELECT coalesce(sum(cantidad), 0) FROM reservas WHERE NOT liberada");
        if (libres < 0) v.add("existencias negativas");
        if (libres + vigentes != existenciasIniciales) v.add("existencias + reservadas = " + (libres + vigentes) + ", esperado " + existenciasIniciales);
        return v;
    }

    @Override
    public void close() throws InterruptedException {
        for (Process p : procesos) if (p.isAlive()) matar(p);
    }
}
