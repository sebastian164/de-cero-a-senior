package tienda.inventario;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API pública del módulo inventario. Dueño de inventario_stock e inventario_reservas. */
@Service
public class Inventario {
    public static class SinStock extends RuntimeException {
        public SinStock(String sku) { super("Sin stock suficiente de " + sku); }
    }

    private final JdbcClient jdbc;

    public Inventario(JdbcClient jdbc) { this.jdbc = jdbc; }

    public void ingresar(String sku, int cantidad) {
        jdbc.sql("INSERT INTO inventario_stock (sku, disponible) VALUES (?,?) ON CONFLICT (sku) DO UPDATE SET disponible = inventario_stock.disponible + EXCLUDED.disponible")
                .params(sku, cantidad).update();
    }

    public int disponible(String sku) {
        return jdbc.sql("SELECT disponible FROM inventario_stock WHERE sku = ?").param(sku).query(Integer.class).optional().orElse(0);
    }

    /** Descuento atómico: el UPDATE condicionado evita vender de más aunque haya concurrencia. */
    @Transactional
    public void reservar(long pedidoId, String sku, int cantidad) {
        int filas = jdbc.sql("UPDATE inventario_stock SET disponible = disponible - ? WHERE sku = ? AND disponible >= ?")
                .params(cantidad, sku, cantidad).update();
        if (filas == 0) throw new SinStock(sku);
        jdbc.sql("INSERT INTO inventario_reservas (pedido_id, sku, cantidad) VALUES (?,?,?)").params(pedidoId, sku, cantidad).update();
    }

    /** Idempotente: liberar dos veces la misma reserva devuelve el stock una sola vez. */
    @Transactional
    public void liberar(long pedidoId) {
        var r = jdbc.sql("UPDATE inventario_reservas SET liberada = true WHERE pedido_id = ? AND NOT liberada RETURNING sku, cantidad")
                .param(pedidoId).query((rs, n) -> new Object[]{rs.getString(1), rs.getInt(2)}).optional();
        r.ifPresent(x -> jdbc.sql("UPDATE inventario_stock SET disponible = disponible + ? WHERE sku = ?").params(x[1], x[0]).update());
    }
}
