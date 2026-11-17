package tienda.integrador.fidelidad.adaptadores.salida.jdbc;

import org.springframework.jdbc.core.JdbcTemplate;
import tienda.integrador.fidelidad.aplicacion.Puertos.Puntos;

/** Adaptador de salida de Fidelidad: su base (esquema s07_fidelidad). */
public final class FidelidadJdbc implements Puntos {

    private final JdbcTemplate jdbc;

    public FidelidadJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        jdbc.execute("CREATE TABLE IF NOT EXISTS puntos(pedido_id text PRIMARY KEY, puntos int)");
    }

    @Override
    public boolean sumar(String pedidoId, int puntos) {
        return jdbc.update("INSERT INTO puntos(pedido_id, puntos) VALUES (?, ?) ON CONFLICT DO NOTHING", pedidoId, puntos) == 1;
    }
}
