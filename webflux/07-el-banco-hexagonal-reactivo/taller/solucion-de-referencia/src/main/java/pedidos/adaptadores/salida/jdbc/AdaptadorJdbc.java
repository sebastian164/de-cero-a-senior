package pedidos.adaptadores.salida.jdbc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import pedidos.aplicacion.sincrona.puertos.*;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.dominio.*;

public class AdaptadorJdbc implements RepositorioDePedidos, Inventario, UnidadDeTrabajo {
    private final JdbcTemplate jdbc; private final TransactionTemplate tx;
    public AdaptadorJdbc(JdbcTemplate jdbc, TransactionTemplate tx) { this.jdbc = jdbc; this.tx = tx; }

    public Optional<Pedido> buscar(String id) {
        // Dos consultas en secuencia: la conexión de la cabecera se devuelve antes de pedir la de las líneas
        // (anidarlas en el RowMapper retiene dos conexiones por petición fuera de una transacción).
        List<Object[]> cabecera = jdbc.query("select cliente, estado, creado_en from pedido where id = ?",
                (rs, n) -> new Object[]{rs.getString(1), rs.getString(2), rs.getObject(3, OffsetDateTime.class).toInstant()}, id);
        if (cabecera.isEmpty()) return Optional.empty();
        List<LineaDePedido> lineas = jdbc.query("select producto, cantidad, precio_centavos from linea_pedido where pedido_id = ? order by id",
                (l, i) -> new LineaDePedido(l.getString(1), l.getInt(2), new Dinero(l.getLong(3))), id);
        Object[] c = cabecera.get(0);
        return Optional.of(Pedido.existente(id, (String) c[0], lineas, EstadoDePedido.valueOf((String) c[1]), (java.time.Instant) c[2]));
    }
    public void guardar(Pedido pedido, EstadoDePedido leido) {
        if (jdbc.update("update pedido set estado = ? where id = ? and estado = ?", pedido.estado().name(), pedido.id(), leido.name()) != 1)
            throw new ConflictoDeConcurrencia("el pedido " + pedido.id());
    }
    public Optional<Existencias> existenciasDe(String producto) {
        return jdbc.queryForList("select cantidad from existencias where producto = ? for update", Integer.class, producto).stream().findFirst()
                .map(c -> Existencias.de(producto, c));
    }
    public void guardar(Existencias e) { jdbc.update("update existencias set cantidad = ? where producto = ?", e.unidades(), e.producto()); }
    public <T> T ejecutar(Supplier<T> trabajo) { return tx.execute(s -> trabajo.get()); }
}
