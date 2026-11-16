package transacciones;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Acceso a la tabla. Inserta y, si el total es negativo, falla DESPUÉS de insertar. */
@Component
public class Pedidos {
    private final JdbcClient jdbc;

    public Pedidos(JdbcClient jdbc) { this.jdbc = jdbc; }

    public void insertarYValidar(PedidoDto dto) {
        jdbc.sql("INSERT INTO n703_pedidos(cliente, total) VALUES (?, ?)").params(dto.cliente(), dto.total()).update();
        if (dto.total() < 0) throw new IllegalArgumentException("total negativo para " + dto.cliente());
    }

    public long contar() { return jdbc.sql("SELECT count(*) FROM n703_pedidos").query(Long.class).single(); }
    public void vaciar() { jdbc.sql("DELETE FROM n703_pedidos").update(); jdbc.sql("DELETE FROM n703_auditoria").update(); }
    public long auditorias(String resultado) {
        return jdbc.sql("SELECT count(*) FROM n703_auditoria WHERE resultado = ?").param(resultado).query(Long.class).single();
    }
}
