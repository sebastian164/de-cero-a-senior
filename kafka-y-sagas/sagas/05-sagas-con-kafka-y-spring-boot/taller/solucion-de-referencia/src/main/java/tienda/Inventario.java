package tienda;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tienda.Mensajes.Orden;

/** Reserva y libera existencias. Al liberar sin reserva deja una lápida: si la reserva llega tarde, no prospera. */
public class Inventario extends Participante {

    public Inventario(JdbcTemplate jdbc, TransactionTemplate tx) {
        super(jdbc, tx);
    }

    @Override
    protected String ejecutar(Orden o) {
        return switch (o.tipo()) {
            case "reservar-inventario" -> reservar(o);
            case "liberar-inventario" -> liberar(o);
            default -> throw new IllegalArgumentException("orden desconocida: " + o.tipo());
        };
    }

    private String reservar(Orden o) {
        if (!jdbc.queryForList("SELECT 1 FROM lapidas WHERE saga_id = ?", o.sagaId()).isEmpty()) return "sin-existencias";       // ya se liberó: la reserva llega tarde
        int filas = jdbc.update("UPDATE stock SET disponibles = disponibles - ? WHERE producto = ? AND disponibles >= ?", o.cantidad(), o.producto(), o.cantidad());
        if (filas != 1) return "sin-existencias";
        jdbc.update("INSERT INTO reservas(saga_id, producto, cantidad) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", o.sagaId(), o.producto(), o.cantidad());
        return "inventario-reservado";
    }

    private String liberar(Orden o) {
        if (fallarSiempreLaCompensacion) throw new IllegalStateException("la compensación de inventario está rota (fallo programado)");
        int filas = jdbc.update("UPDATE reservas SET liberada = true WHERE saga_id = ? AND NOT liberada", o.sagaId());
        if (filas == 1) {
            jdbc.update("UPDATE stock SET disponibles = disponibles + (SELECT cantidad FROM reservas WHERE saga_id = ?) WHERE producto = (SELECT producto FROM reservas WHERE saga_id = ?)", o.sagaId(), o.sagaId());
        } else {
            jdbc.update("INSERT INTO lapidas(saga_id) VALUES (?) ON CONFLICT DO NOTHING", o.sagaId());                            // nada que liberar todavía: lápida
        }
        return "inventario-liberado";
    }
}
