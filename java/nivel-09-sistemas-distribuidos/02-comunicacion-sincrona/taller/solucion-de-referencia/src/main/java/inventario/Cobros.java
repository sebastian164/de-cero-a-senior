package inventario;

import inventario.ControladorInventario.CobroPedido;
import inventario.ControladorInventario.ResultadoCobro;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Cobros {

    private final JdbcTemplate jdbc;

    Cobros(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public ResultadoCobro cobrar(CobroPedido c) {
        Long id = jdbc.queryForObject("INSERT INTO cobros (pedido_id, monto) VALUES (?, ?) RETURNING id", Long.class,
                c.pedidoId(), c.monto());
        return new ResultadoCobro(id, c.pedidoId(), "APROBADO");
    }

    /**
     * La clave se inserta primero, en la misma transacción que el cobro. Con dos peticiones simultáneas con la
     * misma clave, la segunda queda esperando en el índice único hasta que la primera confirma; entonces
     * ON CONFLICT DO NOTHING no inserta nada y se devuelve el resultado guardado. Nunca se cobra dos veces.
     */
    @Transactional
    public ResultadoCobro cobrarUnaVez(String clave, CobroPedido c) {
        int insertadas = jdbc.update("INSERT INTO claves_idempotencia (clave, pedido_id) VALUES (?, ?) ON CONFLICT (clave) DO NOTHING",
                clave, c.pedidoId());
        if (insertadas == 0) {
            return jdbc.queryForObject("SELECT cobro_id, pedido_id FROM claves_idempotencia WHERE clave = ?",
                    (rs, i) -> new ResultadoCobro(rs.getLong(1), rs.getLong(2), "APROBADO"), clave);
        }
        ResultadoCobro r = cobrar(c);
        jdbc.update("UPDATE claves_idempotencia SET cobro_id = ? WHERE clave = ?", r.cobroId(), clave);
        return r;
    }

    public int contar(long pedidoId) {
        return jdbc.queryForObject("SELECT count(*) FROM cobros WHERE pedido_id = ?", Integer.class, pedidoId);
    }
}
