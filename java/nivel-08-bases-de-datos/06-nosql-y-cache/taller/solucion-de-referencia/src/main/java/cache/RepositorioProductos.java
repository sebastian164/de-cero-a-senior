package cache;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL real. La consulta lleva pg_sleep(0.02) para simular una consulta costosa, y se cuentan las llamadas. */
@Repository
public class RepositorioProductos {

    private final JdbcTemplate jdbc;
    private final AtomicInteger consultas = new AtomicInteger();

    public RepositorioProductos(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ProductoDto buscarDto(Long id) {
        consultas.incrementAndGet();
        return jdbc.queryForObject("SELECT id, nombre, precio FROM productos, pg_sleep(0.02) WHERE id = ?",
                (rs, i) -> new ProductoDto(rs.getLong(1), rs.getString(2), rs.getBigDecimal(3)), id);
    }

    public void actualizarPrecio(Long id, BigDecimal precio) {
        jdbc.update("UPDATE productos SET precio = ? WHERE id = ?", precio, id);
    }

    public int consultas() {
        return consultas.get();
    }

    public void reiniciarContador() {
        consultas.set(0);
    }
}
