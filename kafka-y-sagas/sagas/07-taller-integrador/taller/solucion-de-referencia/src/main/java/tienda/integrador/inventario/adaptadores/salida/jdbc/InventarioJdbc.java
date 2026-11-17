package tienda.integrador.inventario.adaptadores.salida.jdbc;

import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tienda.integrador.inventario.aplicacion.Puertos.BandejaDeSalida;
import tienda.integrador.inventario.aplicacion.Puertos.RegistroDeRespuestas;
import tienda.integrador.inventario.aplicacion.Puertos.UnidadDeTrabajo;
import tienda.integrador.inventario.aplicacion.Puertos.Existencias;
import tienda.integrador.plataforma.PublicadorDeSalida;

/** Adaptador de salida sobre PostgreSQL: su propia base (esquema s07_inventario), la tabla de respuestas y la tabla de salida. */
public final class InventarioJdbc implements RegistroDeRespuestas, BandejaDeSalida, UnidadDeTrabajo, Existencias {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public InventarioJdbc(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
        jdbc.execute("CREATE TABLE IF NOT EXISTS respuestas(orden_id text PRIMARY KEY, tipo text NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS stock(producto text PRIMARY KEY, disponibles int NOT NULL CHECK (disponibles >= 0))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS reservas(saga_id text PRIMARY KEY, producto text, cantidad int, liberada boolean NOT NULL DEFAULT false)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS lapidas(saga_id text PRIMARY KEY)");
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
    public boolean hayLapida(String sagaId) {
        return !jdbc.queryForList("SELECT 1 FROM lapidas WHERE saga_id = ?", sagaId).isEmpty();
    }

    @Override
    public boolean descontar(String producto, int cantidad) {
        return jdbc.update("UPDATE stock SET disponibles = disponibles - ? WHERE producto = ? AND disponibles >= ?", cantidad, producto, cantidad) == 1;
    }

    @Override
    public void registrarReserva(String sagaId, String producto, int cantidad) {
        jdbc.update("INSERT INTO reservas(saga_id, producto, cantidad) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", sagaId, producto, cantidad);
    }

    @Override
    public boolean liberarReserva(String sagaId) {
        if (jdbc.update("UPDATE reservas SET liberada = true WHERE saga_id = ? AND NOT liberada", sagaId) != 1) return false;
        jdbc.update("UPDATE stock SET disponibles = disponibles + (SELECT cantidad FROM reservas WHERE saga_id = ?) WHERE producto = (SELECT producto FROM reservas WHERE saga_id = ?)", sagaId, sagaId);
        return true;
    }

    @Override
    public void ponerLapida(String sagaId) {
        jdbc.update("INSERT INTO lapidas(saga_id) VALUES (?) ON CONFLICT DO NOTHING", sagaId);
    }
}
