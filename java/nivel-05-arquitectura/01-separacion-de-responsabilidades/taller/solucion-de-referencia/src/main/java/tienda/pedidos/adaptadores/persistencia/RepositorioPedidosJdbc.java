package tienda.pedidos.adaptadores.persistencia;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import tienda.pedidos.aplicacion.RepositorioPedidos;
import tienda.pedidos.dominio.Pedido;

/** Infraestructura: el único lugar que conoce SQL. Cambiar de base toca solo esta clase. */
@Repository
class RepositorioPedidosJdbc implements RepositorioPedidos {

    private final JdbcClient jdbc;

    RepositorioPedidosJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Pedido guardar(Pedido pedido) {
        var llave = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO pedidos(cliente, cantidad, total) VALUES (?, ?, ?)")
                .params(pedido.cliente(), pedido.cantidad(), pedido.total())
                .update(llave, "id");
        return pedido.conId(llave.getKey().longValue());
    }
}
