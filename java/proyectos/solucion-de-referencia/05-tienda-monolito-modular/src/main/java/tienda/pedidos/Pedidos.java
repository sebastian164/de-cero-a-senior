package tienda.pedidos;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tienda.catalogo.Catalogo;
import tienda.eventos.Outbox;
import tienda.inventario.Inventario;
import tienda.pagos.Pagos;
import tienda.pedidos.interno.Fallos;

import java.math.BigDecimal;
import java.util.List;

/**
 * API pública del módulo pedidos. Confirmar un pedido es una saga orquestada:
 * reservar inventario (síncrono) -> cobrar (síncrono, protegido) -> confirmar + evento (una transacción).
 * Si algo falla, se compensa en orden inverso.
 */
@Service
public class Pedidos {
    public record Pedido(long id, String cliente, String sku, int cantidad, BigDecimal total, String estado, String motivo) {}

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Pedidos.class);

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final Catalogo catalogo;
    private final Inventario inventario;
    private final Pagos pagos;
    private final Outbox outbox;
    private final Fallos fallos;

    public Pedidos(JdbcClient jdbc, TransactionTemplate tx, Catalogo catalogo, Inventario inventario, Pagos pagos, Outbox outbox, Fallos fallos) {
        this.jdbc = jdbc; this.tx = tx; this.catalogo = catalogo; this.inventario = inventario; this.pagos = pagos; this.outbox = outbox; this.fallos = fallos;
    }

    public Pedido confirmar(String cliente, String sku, int cantidad) {
        BigDecimal total = catalogo.precio(sku).multiply(BigDecimal.valueOf(cantidad));
        long id = jdbc.sql("INSERT INTO pedidos_pedidos (cliente, sku, cantidad, total, estado) VALUES (?,?,?,?,'PENDIENTE') RETURNING id")
                .params(cliente, sku, cantidad, total).query(Long.class).single();
        boolean reservado = false, cobrado = false;
        try {
            fallos.en("reserva");
            inventario.reservar(id, sku, cantidad);
            reservado = true;
            fallos.en("cobro");
            pagos.cobrar(id, total);
            cobrado = true;
            tx.executeWithoutResult(s -> {
                fallos.en("confirmacion");
                jdbc.sql("UPDATE pedidos_pedidos SET estado = 'CONFIRMADO' WHERE id = ?").param(id).update();
                outbox.publicar("PedidoConfirmado", String.valueOf(id), String.valueOf(id));
            });
        } catch (RuntimeException e) {
            String motivo = (e.getClass().getSimpleName() + ": " + e.getMessage());
            motivo = motivo.length() > 190 ? motivo.substring(0, 190) : motivo;
            if (cobrado) pagos.reembolsar(id);
            else if (e instanceof Pagos.CobroIncierto) pagos.anular(id);   // tiempo vencido: puede que sí se cobrara
            if (reservado) inventario.liberar(id);
            String m = motivo;
            tx.executeWithoutResult(s -> {
                jdbc.sql("UPDATE pedidos_pedidos SET estado = 'RECHAZADO', motivo = ? WHERE id = ?").params(m, id).update();
                outbox.publicar("PedidoRechazado", String.valueOf(id), String.valueOf(id));
            });
        }
        Pedido resultado = buscar(id);
        log.info("Pedido {} de {} {}", id, cliente, resultado.estado());
        return resultado;
    }

    public Pedido buscar(long id) {
        return jdbc.sql("SELECT id, cliente, sku, cantidad, total, estado, motivo FROM pedidos_pedidos WHERE id = ?").param(id)
                .query((rs, n) -> new Pedido(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getBigDecimal(5), rs.getString(6), rs.getString(7))).single();
    }

    public List<Pedido> deCliente(String cliente) {
        return jdbc.sql("SELECT id, cliente, sku, cantidad, total, estado, motivo FROM pedidos_pedidos WHERE cliente = ? ORDER BY creado DESC LIMIT 20").param(cliente)
                .query((rs, n) -> new Pedido(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getBigDecimal(5), rs.getString(6), rs.getString(7))).list();
    }
}
