package pedidos.adaptadores.salida;

import java.time.OffsetDateTime;
import java.util.function.Supplier;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import pedidos.aplicacion.puertos.*;
import reactor.core.publisher.Mono;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.dominio.*;

public class AdaptadorR2dbc implements RepositorioDePedidos, Almacen, UnidadDeTrabajo {
    private final DatabaseClient db; private final TransactionalOperator tx;
    public AdaptadorR2dbc(DatabaseClient db, TransactionalOperator tx) { this.db = db; this.tx = tx; }

    private record Cabecera(String cliente, String estado, OffsetDateTime creado) {}

    public Mono<Pedido> buscar(String id) {
        return db.sql("select cliente, estado, creado_en from pedido where id = :id").bind("id", id)
                .map(f -> new Cabecera(f.get("cliente", String.class), f.get("estado", String.class), f.get("creado_en", OffsetDateTime.class))).one()
                .flatMap(c -> db.sql("select producto, cantidad, precio_centavos from linea_pedido where pedido_id = :id order by id").bind("id", id)
                        .map(f -> new LineaDePedido(f.get("producto", String.class), f.get("cantidad", Integer.class), new Dinero(f.get("precio_centavos", Long.class)))).all().collectList()
                        .map(lineas -> Pedido.existente(id, c.cliente(), lineas, EstadoDePedido.valueOf(c.estado()), c.creado().toInstant())));
    }
    public Mono<Void> guardar(Pedido pedido, EstadoDePedido leido) {
        return db.sql("update pedido set estado = :nuevo where id = :id and estado = :leido")
                .bind("nuevo", pedido.estado().name()).bind("id", pedido.id()).bind("leido", leido.name()).fetch().rowsUpdated()
                .flatMap(n -> n == 1 ? Mono.<Void>empty() : Mono.<Void>error(new ConflictoDeConcurrencia("el pedido " + pedido.id())));
    }
    public Mono<Existencias> existenciasDe(String producto) {
        return db.sql("select cantidad from existencias where producto = :p for update").bind("p", producto)
                .map(f -> Existencias.de(producto, f.get("cantidad", Integer.class))).one();
    }
    public Mono<Void> guardar(Existencias e) {
        return db.sql("update existencias set cantidad = :c where producto = :p").bind("c", e.unidades()).bind("p", e.producto()).fetch().rowsUpdated().then();
    }
    public <T> Mono<T> ejecutar(Supplier<Mono<T>> trabajo) { return Mono.defer(trabajo).as(tx::transactional); }
}
