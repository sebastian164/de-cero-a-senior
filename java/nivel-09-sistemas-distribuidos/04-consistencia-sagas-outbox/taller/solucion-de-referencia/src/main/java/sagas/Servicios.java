package sagas;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntSupplier;

/** Servicios simulados en memoria. Sus operaciones y compensaciones son idempotentes. */
public final class Servicios {

    private Servicios() { }

    /** Servicio genérico: lleva el conjunto de pedidos "aplicados" y el registro de llamadas. */
    public static class Simulado implements Saga.Participante {
        private final String nombre;
        public final Set<Long> aplicados = ConcurrentSet.nuevo();
        public final List<String> llamadas = new CopyOnWriteArrayList<>();
        public volatile boolean fallaAlEjecutar;
        public volatile int fallosDeCompensacion;          // cuántas compensaciones fallan antes de funcionar

        public Simulado(String nombre) { this.nombre = nombre; }

        @Override public String nombre() { return nombre; }

        @Override public void ejecutar(long pedidoId) {
            llamadas.add("ejecutar:" + pedidoId);
            if (fallaAlEjecutar) {
                throw new IllegalStateException(nombre + " no puede");
            }
            aplicados.add(pedidoId);                       // idempotente: un conjunto
        }

        @Override public void compensar(long pedidoId) {
            llamadas.add("compensar:" + pedidoId);
            if (fallosDeCompensacion > 0) {
                fallosDeCompensacion--;
                throw new IllegalStateException(nombre + " no responde");
            }
            aplicados.remove(pedidoId);                    // idempotente: quitar dos veces es igual que una
        }
    }

    /** Servicio cuyo efecto vive en PostgreSQL: sobrevive a la muerte del orquestador. Idempotente en ambos sentidos. */
    public static class EnBase implements Saga.Participante {
        public static final String ESQUEMA = "CREATE TABLE IF NOT EXISTS efectos (pedido_id BIGINT, servicio TEXT, PRIMARY KEY (pedido_id, servicio))";
        private final String url, nombre;
        public volatile boolean falla;
        public volatile Runnable antes = () -> { };

        public EnBase(String url, String nombre) { this.url = url; this.nombre = nombre; }

        @Override public String nombre() { return nombre; }

        @Override public void ejecutar(long pedidoId) {
            antes.run();
            if (falla) throw new IllegalStateException(nombre + " no puede");
            sql("INSERT INTO efectos VALUES (?, ?) ON CONFLICT DO NOTHING", pedidoId);
        }

        @Override public void compensar(long pedidoId) { sql("DELETE FROM efectos WHERE pedido_id = ? AND servicio = ?", pedidoId); }

        private void sql(String sentencia, long pedidoId) {
            try (var c = soporte.Entorno.conectar(url); var ps = c.prepareStatement(sentencia)) {
                ps.setLong(1, pedidoId);
                ps.setString(2, nombre);
                ps.executeUpdate();
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException(e);
            }
        }

        public static java.util.List<String> efectos(String url, long pedidoId) throws java.sql.SQLException {
            try (var c = soporte.Entorno.conectar(url); var ps = c.prepareStatement("SELECT servicio FROM efectos WHERE pedido_id = ? ORDER BY servicio")) {
                ps.setLong(1, pedidoId);
                var rs = ps.executeQuery();
                var r = new java.util.ArrayList<String>();
                while (rs.next()) r.add(rs.getString(1));
                return r;
            }
        }
    }

    static final class ConcurrentSet {
        static Set<Long> nuevo() { return java.util.concurrent.ConcurrentHashMap.newKeySet(); }
    }
}
