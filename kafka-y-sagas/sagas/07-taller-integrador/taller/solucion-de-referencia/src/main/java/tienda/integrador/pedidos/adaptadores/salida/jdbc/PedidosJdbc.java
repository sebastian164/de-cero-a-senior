package tienda.integrador.pedidos.adaptadores.salida.jdbc;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tienda.integrador.pedidos.aplicacion.Puertos.BandejaDeSalida;
import tienda.integrador.pedidos.aplicacion.Puertos.MensajesProcesados;
import tienda.integrador.pedidos.aplicacion.Puertos.RepositorioDePedidos;
import tienda.integrador.pedidos.aplicacion.Puertos.UnidadDeTrabajo;
import tienda.integrador.pedidos.dominio.Pedido;
import tienda.integrador.plataforma.PublicadorDeSalida;

/** Adaptador de salida de Pedidos sobre PostgreSQL: su base (esquema s07_pedidos), sus eventos en la tabla de salida y su unidad de trabajo. */
public final class PedidosJdbc implements RepositorioDePedidos, MensajesProcesados, BandejaDeSalida, UnidadDeTrabajo {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public PedidosJdbc(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
        jdbc.execute("CREATE TABLE IF NOT EXISTS pedidos(id text PRIMARY KEY, correlacion text NOT NULL, producto text, cantidad int, centavos bigint, ciudad text, "
                + "estado text NOT NULL DEFAULT 'PENDIENTE', motivo text, creado timestamptz DEFAULT clock_timestamp())");
        jdbc.execute("CREATE TABLE IF NOT EXISTS procesados(id text PRIMARY KEY)");
    }

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        return tx.execute(estado -> trabajo.get());
    }

    @Override
    public boolean crear(Pedido p) {
        return jdbc.update("INSERT INTO pedidos(id, correlacion, producto, cantidad, centavos, ciudad) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                p.id(), p.correlacion(), p.producto(), p.cantidad(), p.centavos(), p.ciudad()) == 1;
    }

    @Override
    public Optional<Pedido> buscar(String id) {
        return jdbc.queryForList("SELECT * FROM pedidos WHERE id = ?", id).stream().findFirst().map(PedidosJdbc::pedido);
    }

    private static Pedido pedido(Map<String, Object> f) {
        return new Pedido((String) f.get("id"), (String) f.get("correlacion"), (String) f.get("producto"), ((Number) f.get("cantidad")).intValue(),
                ((Number) f.get("centavos")).longValue(), (String) f.get("ciudad"), Pedido.Estado.valueOf((String) f.get("estado")), (String) f.get("motivo"));
    }

    @Override
    public void cerrar(String id, Pedido.Estado estado, String motivo) {
        jdbc.update("UPDATE pedidos SET estado = ?, motivo = ? WHERE id = ?", estado.name(), motivo, id);
    }

    @Override
    public long contarPendientes() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM pedidos WHERE estado = 'PENDIENTE'", Long.class);
        return n == null ? 0 : n;
    }

    @Override
    public boolean marcarNuevo(String id) {
        return jdbc.update("INSERT INTO procesados(id) VALUES (?) ON CONFLICT DO NOTHING", id) == 1;
    }

    /** El formato del evento vive aquí, en el adaptador. */
    @Override
    public void anunciarPedidoCreado(Pedido p) {
        PublicadorDeSalida.poner(jdbc, "compra.pedidos", p.id(), "{\"id\":\"" + p.id() + "/PedidoCreado\",\"tipo\":\"PedidoCreado\",\"version\":1,\"correlacion\":\"" + p.correlacion()
                + "\",\"datos\":{\"pedidoId\":\"" + p.id() + "\",\"producto\":\"" + p.producto() + "\",\"cantidad\":" + p.cantidad() + ",\"centavos\":" + p.centavos()
                + ",\"ciudad\":\"" + p.ciudad() + "\"}}");
    }
}
