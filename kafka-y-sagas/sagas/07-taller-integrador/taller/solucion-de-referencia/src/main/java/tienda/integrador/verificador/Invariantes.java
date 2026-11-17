package tienda.integrador.verificador;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/** El verificador de invariantes de la tienda (parte de la entrega). Es una herramienta de operación: lee las bases de los servicios. */
public final class Invariantes {

    private Invariantes() {
    }

    /** Recorre las bases y devuelve las violaciones de las invariantes de la tienda. Vacío = todo en orden. */
    public static List<String> verificar(Map<String, JdbcTemplate> bd, long existenciasIniciales, long maxPendienteSegundos) {
        List<String> v = new java.util.ArrayList<>();
        JdbcTemplate pedidos = bd.get("pedidos"), pagos = bd.get("pagos"), inv = bd.get("inventario"), env = bd.get("envios"), fid = bd.get("fidelidad");
        for (Map<String, Object> p : pedidos.queryForList("SELECT id, estado, extract(epoch FROM clock_timestamp() - creado) AS edad FROM pedidos")) {
            String id = (String) p.get("id");
            String estado = (String) p.get("estado");
            long cobros = pagos.queryForObject("SELECT count(*) FROM cobros WHERE saga_id = ? AND NOT reembolsado", Long.class, id);
            long cobrosTotales = pagos.queryForObject("SELECT count(*) FROM cobros WHERE saga_id = ?", Long.class, id);
            long reservas = inv.queryForObject("SELECT count(*) FROM reservas WHERE saga_id = ? AND NOT liberada", Long.class, id);
            long despachos = env.queryForObject("SELECT count(*) FROM despachos WHERE saga_id = ?", Long.class, id);
            if (cobrosTotales > 1) v.add(id + ": cobro doble");
            switch (estado) {
                case "CONFIRMADO" -> { if (cobros != 1 || reservas != 1 || despachos != 1) v.add(id + ": CONFIRMADO sin exactamente un cobro, una reserva y un despacho (" + cobros + "/" + reservas + "/" + despachos + ")"); }
                case "CANCELADO" -> { if (cobros != 0 || reservas != 0 || despachos != 0) v.add(id + ": CANCELADO con cobro, reserva o despacho vigente (" + cobros + "/" + reservas + "/" + despachos + ")"); }
                default -> { if (((Number) p.get("edad")).doubleValue() > maxPendienteSegundos) v.add(id + ": PENDIENTE desde hace " + p.get("edad") + " s"); }
            }
            if (fid != null) {
                long puntos = fid.queryForObject("SELECT count(*) FROM puntos WHERE pedido_id = ?", Long.class, id);
                if (puntos > 0 && !estado.equals("CONFIRMADO")) v.add(id + ": puntos sumados a un pedido " + estado);
            }
        }
        long libres = inv.queryForObject("SELECT coalesce(sum(disponibles), 0) FROM stock", Long.class);
        long reservadas = inv.queryForObject("SELECT coalesce(sum(cantidad), 0) FROM reservas WHERE NOT liberada", Long.class);
        if (libres < 0) v.add("existencias negativas");
        if (libres + reservadas != existenciasIniciales) v.add("existencias " + libres + " + reservadas " + reservadas + " != " + existenciasIniciales);
        return v;
    }

}
