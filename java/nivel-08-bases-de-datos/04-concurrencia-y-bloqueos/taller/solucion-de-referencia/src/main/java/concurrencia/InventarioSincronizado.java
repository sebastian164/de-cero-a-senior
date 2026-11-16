package concurrencia;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Paso 6: "protegido" con synchronized. Funciona dentro de una instancia; no entre dos. */
@Service
public class InventarioSincronizado {

    private final JdbcTemplate jdbc;

    public InventarioSincronizado(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public synchronized void vender(long id) {
        Integer stock = jdbc.queryForObject("SELECT stock FROM productos WHERE id = ?", Integer.class, id);
        ServicioProductos.pausa();
        jdbc.update("UPDATE productos SET stock = ?, version = version + 1 WHERE id = ?", stock - 1, id);
    }
}
