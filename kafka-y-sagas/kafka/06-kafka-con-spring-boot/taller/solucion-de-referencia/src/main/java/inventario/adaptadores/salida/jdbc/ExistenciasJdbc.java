package inventario.adaptadores.salida.jdbc;

import inventario.aplicacion.Puertos.EventosProcesados;
import inventario.aplicacion.Puertos.Existencias;
import inventario.dominio.PedidoConfirmado.Linea;
import inventario.dominio.ResultadoDeReserva;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/** Los dos puertos de persistencia sobre la misma base: comparten la transacción del puerto Transaccion. */
public class ExistenciasJdbc implements Existencias, EventosProcesados {

    private final JdbcTemplate jdbc;

    public ExistenciasJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean reservar(String pedidoId, List<Linea> lineas) {
        // Se ordenan y se bloquean las filas en el mismo orden siempre: dos pedidos a la vez no se bloquean en cruz
        List<Linea> ordenadas = lineas.stream().sorted(java.util.Comparator.comparing(Linea::productoId)).toList();
        for (Linea l : ordenadas) {
            List<Integer> hay = jdbc.query("SELECT existencias FROM stock WHERE producto_id = ? FOR UPDATE", (rs, n) -> rs.getInt(1), l.productoId());
            if (hay.isEmpty() || hay.get(0) < l.cantidad()) {
                return false;                                              // nada se ha descontado todavía
            }
        }
        for (Linea l : ordenadas) {
            jdbc.update("UPDATE stock SET existencias = existencias - ? WHERE producto_id = ?", l.cantidad(), l.productoId());
        }
        return true;
    }

    @Override
    public boolean registrarSiEsNuevo(String eventoId) {
        return jdbc.update("INSERT INTO procesados(evento_id) VALUES (?) ON CONFLICT DO NOTHING", eventoId) == 1;
    }

    @Override
    public void guardarResultado(String eventoId, ResultadoDeReserva resultado) {
        jdbc.update("UPDATE procesados SET resultado = ? WHERE evento_id = ?", resultado.name(), eventoId);
    }

    @Override
    public Optional<ResultadoDeReserva> resultadoDe(String eventoId) {
        return jdbc.query("SELECT resultado FROM procesados WHERE evento_id = ? AND resultado IS NOT NULL", (rs, n) -> ResultadoDeReserva.valueOf(rs.getString(1)), eventoId)
                .stream().findFirst();
    }
}
