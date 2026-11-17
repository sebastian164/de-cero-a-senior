package tienda;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tienda.Mensajes.Orden;

/**
 * El patrón común de los tres participantes: en UNA transacción local, (1) se comprueba si la orden ya se vio, (2) se aplica el
 * efecto con su propia idempotencia, (3) se anota la respuesta y se deja en la tabla de salida. Una orden repetida repite la
 * respuesta guardada, sin repetir el efecto: la respuesta pudo perderse.
 */
public abstract class Participante {

    private static final Logger LOG = LoggerFactory.getLogger(Participante.class);
    protected final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    /** Fallo programado para las pruebas: la compensación falla siempre. */
    public volatile boolean fallarSiempreLaCompensacion;

    protected Participante(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    /** Aplica el efecto de la orden y devuelve el tipo de respuesta. Corre dentro de la transacción. */
    protected abstract String ejecutar(Orden orden);

    public void atender(Orden orden) {
        tx.executeWithoutResult(estado -> {
            List<String> previa = jdbc.query("SELECT tipo FROM respuestas WHERE orden_id = ?", (rs, n) -> rs.getString(1), orden.id());
            String tipo;
            if (previa.isEmpty()) {
                tipo = ejecutar(orden);
                jdbc.update("INSERT INTO respuestas(orden_id, tipo) VALUES (?, ?)", orden.id(), tipo);
            } else {
                tipo = previa.get(0);                                                            // orden repetida: misma respuesta
            }
            Salida.poner(jdbc, Mensajes.RESPUESTAS, orden.sagaId(), new Mensajes.Respuesta(orden.id() + "->" + tipo, orden.sagaId(), orden.correlacion(), tipo).json());
            LOG.info("correlacion={} saga={} {} -> {}", orden.correlacion(), orden.sagaId(), orden.tipo(), tipo);
        });
    }

    public Map<String, Object> contar(String sql) {
        return jdbc.queryForMap(sql);
    }
}
