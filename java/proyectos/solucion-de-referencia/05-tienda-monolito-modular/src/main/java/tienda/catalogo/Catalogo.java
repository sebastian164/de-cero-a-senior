package tienda.catalogo;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** API pública del módulo catálogo. */
@Service
public class Catalogo {
    private final JdbcClient jdbc;

    public Catalogo(JdbcClient jdbc) { this.jdbc = jdbc; }

    public void crear(String sku, String nombre, BigDecimal precio) {
        jdbc.sql("INSERT INTO catalogo_productos (sku, nombre, precio) VALUES (?,?,?) ON CONFLICT (sku) DO UPDATE SET nombre = EXCLUDED.nombre, precio = EXCLUDED.precio")
                .params(sku, nombre, precio).update();
    }

    public BigDecimal precio(String sku) {
        return jdbc.sql("SELECT precio FROM catalogo_productos WHERE sku = ?").param(sku).query(BigDecimal.class).optional()
                .orElseThrow(() -> new IllegalArgumentException("Producto desconocido: " + sku));
    }
}
