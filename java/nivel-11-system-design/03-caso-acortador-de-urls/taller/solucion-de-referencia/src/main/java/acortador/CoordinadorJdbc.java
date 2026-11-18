package acortador;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Coordinador de rangos respaldado por una secuencia de PostgreSQL: cada nextval es un bloque. */
@Component
public class CoordinadorJdbc implements CoordinadorDeRangos {
    private final JdbcClient jdbc;

    public CoordinadorJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long siguienteBloque() {
        return jdbc.sql("SELECT nextval('rangos')").query(Long.class).single();
    }
}
