package pedidos;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pedidos.Errores.*;

/** JDBC puro: cada método bloquea el hilo que lo llama. */
@Service
public class ServicioDePedidos {
    private final JdbcTemplate jdbc; private final TransactionTemplate tx; private final int lentoMs;

    public ServicioDePedidos(JdbcTemplate jdbc, TransactionTemplate tx, @Value("${consulta-lenta-ms:0}") int lentoMs) {
        this.jdbc = jdbc; this.tx = tx; this.lentoMs = lentoMs;
    }

    public String confirmar(String id, String clave) {
        return tx.execute(estadoTx -> {
            List<String> estados = jdbc.queryForList("select estado from pedido where id = ?", String.class, id);
            if (estados.isEmpty()) throw new PedidoNoEncontrado(id);
            if (estados.get(0).equals("CONFIRMADO")) return "YA_CONFIRMADO";
            try {
                if (clave != null) jdbc.update("insert into clave_idempotencia (clave, pedido_id) values (?, ?)", clave, id);
            } catch (DuplicateKeyException e) { estadoTx.setRollbackOnly(); return "REPETIDA"; }
            if (jdbc.update("update pedido set estado = 'CONFIRMADO' where id = ? and estado = 'PENDIENTE'", id) == 0) return "YA_CONFIRMADO";
            if (lentoMs > 0) jdbc.queryForObject("select pg_sleep(?)::text", String.class, lentoMs / 1000.0);
            for (var l : jdbc.queryForList("select producto, cantidad from linea_pedido where pedido_id = ? order by producto", id)) {
                int n = jdbc.update("update existencias set cantidad = cantidad - ? where producto = ? and cantidad >= ?", l.get("cantidad"), l.get("producto"), l.get("cantidad"));
                if (n == 0) throw new SinExistencias((String) l.get("producto"));
            }
            return "CONFIRMADO";
        });
    }
}
