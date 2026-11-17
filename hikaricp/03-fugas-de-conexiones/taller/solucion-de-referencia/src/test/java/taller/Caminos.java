package taller;

import com.zaxxer.hikari.HikariDataSource;
import java.util.function.LongFunction;
import javax.sql.DataSource;

/** Utilidades del paso 11: recorrer todos los caminos de un extremo y contar las conexiones que quedan fuera. */
final class Caminos {

    private Caminos() {
    }

    /** Los caminos de "buscar cliente": existe, no existe, identificador inválido. */
    static final long[] CAMINOS = {7, 100_000, -1};

    static int activasTrasTodosLosCaminos(DataSource dataSource, LongFunction<String> extremo) {
        for (int vuelta = 0; vuelta < 3; vuelta++) {
            for (long id : CAMINOS) {
                extremo.apply(id);
            }
        }
        return ((HikariDataSource) dataSource).getHikariPoolMXBean().getActiveConnections();
    }

    static LongFunction<String> sinExcepciones(Extremo extremo) {
        return id -> {
            try {
                return extremo.llamar(id);
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        };
    }

    interface Extremo {
        String llamar(long id) throws Exception;
    }

}
