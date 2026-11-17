package tienda;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tienda.Mensajes.Orden;

/** Cobra y reembolsa contra una pasarela simulada. La clave de idempotencia es el id de la saga: un solo cobro por saga. */
public class Pagos extends Participante {

    /** Importes que la pasarela simulada rechaza. */
    public static final long IMPORTE_RECHAZADO = 666;

    public Pagos(JdbcTemplate jdbc, TransactionTemplate tx) {
        super(jdbc, tx);
    }

    @Override
    protected String ejecutar(Orden o) {
        return switch (o.tipo()) {
            case "cobrar" -> {
                if (!jdbc.queryForList("SELECT 1 FROM lapidas WHERE saga_id = ?", o.sagaId()).isEmpty()) yield "pago-rechazado";   // ya se reembolsó
                if (o.centavos() == IMPORTE_RECHAZADO) yield "pago-rechazado";                                                     // la pasarela dice que no
                jdbc.update("INSERT INTO cobros(saga_id, centavos) VALUES (?, ?) ON CONFLICT (saga_id) DO NOTHING", o.sagaId(), o.centavos());
                yield "pago-aprobado";
            }
            case "reembolsar" -> {
                int filas = jdbc.update("UPDATE cobros SET reembolsado = true WHERE saga_id = ?", o.sagaId());
                if (filas == 0) jdbc.update("INSERT INTO lapidas(saga_id) VALUES (?) ON CONFLICT DO NOTHING", o.sagaId());
                yield "pago-reembolsado";
            }
            default -> throw new IllegalArgumentException("orden desconocida: " + o.tipo());
        };
    }
}
