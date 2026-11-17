package tienda.integrador.envios.adaptadores.salida.jdbc;

import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tienda.integrador.envios.aplicacion.Puertos.BandejaDeSalida;
import tienda.integrador.envios.aplicacion.Puertos.RegistroDeRespuestas;
import tienda.integrador.envios.aplicacion.Puertos.UnidadDeTrabajo;
import tienda.integrador.envios.aplicacion.Puertos.Despachos;
import tienda.integrador.plataforma.PublicadorDeSalida;

/** Adaptador de salida sobre PostgreSQL: su propia base (esquema s07_envios), la tabla de respuestas y la tabla de salida. */
public final class EnviosJdbc implements RegistroDeRespuestas, BandejaDeSalida, UnidadDeTrabajo, Despachos {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public EnviosJdbc(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
        jdbc.execute("CREATE TABLE IF NOT EXISTS respuestas(orden_id text PRIMARY KEY, tipo text NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS despachos(saga_id text PRIMARY KEY, ciudad text)");
    }

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        return tx.execute(estado -> trabajo.get());
    }

    @Override
    public Optional<String> buscar(String ordenId) {
        return jdbc.query("SELECT tipo FROM respuestas WHERE orden_id = ?", (rs, n) -> rs.getString(1), ordenId).stream().findFirst();
    }

    @Override
    public void guardar(String ordenId, String tipo) {
        jdbc.update("INSERT INTO respuestas(orden_id, tipo) VALUES (?, ?)", ordenId, tipo);
    }

    @Override
    public void responder(Respuesta r) {
        PublicadorDeSalida.poner(jdbc, "compra.respuestas", r.sagaId(), "{\"id\":\"" + r.id() + "\",\"version\":1,\"sagaId\":\"" + r.sagaId()
                + "\",\"correlacion\":\"" + r.correlacion() + "\",\"tipo\":\"" + r.tipo() + "\"}");
    }

    @Override
    public void programar(String sagaId, String ciudad) {
        jdbc.update("INSERT INTO despachos(saga_id, ciudad) VALUES (?, ?) ON CONFLICT (saga_id) DO NOTHING", sagaId, ciudad);
    }
}
