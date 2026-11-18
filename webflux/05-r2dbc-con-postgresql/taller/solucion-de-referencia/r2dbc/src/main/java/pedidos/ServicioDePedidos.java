package pedidos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import pedidos.Errores.*;
import reactor.core.publisher.Mono;

@Service
public class ServicioDePedidos {
    private final DatabaseClient db; private final TransactionalOperator tx; private final int lentoMs;

    public ServicioDePedidos(DatabaseClient db, TransactionalOperator tx, @Value("${consulta-lenta-ms:0}") int lentoMs) {
        this.db = db; this.tx = tx; this.lentoMs = lentoMs;
    }

    /** Todo o nada: si cualquier producto no alcanza, se revierte el pedido y los descuentos anteriores. */
    public Mono<String> confirmar(String id, String clave) {
        Mono<String> trabajo = db.sql("select estado from pedido where id = :id").bind("id", id)
                .map(f -> f.get("estado", String.class)).one()
                .switchIfEmpty(Mono.error(() -> new PedidoNoEncontrado(id)))
                .flatMap(estado -> switch (estado) {
                    case "CONFIRMADO" -> Mono.just("YA_CONFIRMADO");
                    case "RECHAZADO" -> Mono.just("RECHAZADO");        // un pedido rechazado no se confirma, y no se llama "ya confirmado"
                    default -> confirmarDeVerdad(id, clave);
                });
        // La clave repetida se atiende FUERA de la transacción: dentro, el error dejaría la transacción abortada en PostgreSQL y el commit fallaría (500).
        return tx.transactional(trabajo).onErrorResume(DuplicateKeyException.class, e -> Mono.just("REPETIDA"));
    }

    private Mono<String> confirmarDeVerdad(String id, String clave) {
        Mono<Void> registrarClave = clave == null ? Mono.empty()
                : db.sql("insert into clave_idempotencia (clave, pedido_id) values (:c, :p)").bind("c", clave).bind("p", id).fetch().rowsUpdated().then();
        return registrarClave
                // el pedido primero: su fila serializa dos confirmaciones del mismo pedido
                .then(db.sql("update pedido set estado = 'CONFIRMADO' where id = :id and estado = 'PENDIENTE'").bind("id", id).fetch().rowsUpdated())
                .flatMap(n -> n == 0 ? Mono.just("YA_CONFIRMADO") : descontar(id).thenReturn("CONFIRMADO"));
    }

    private Mono<Void> descontar(String id) {
        Mono<Void> lenta = lentoMs <= 0 ? Mono.empty()
                : db.sql("select pg_sleep(" + (lentoMs / 1000.0) + ")").fetch().rowsUpdated().then();
        return lenta.thenMany(db.sql("select producto, cantidad from linea_pedido where pedido_id = :id order by producto").bind("id", id)
                        .map(f -> new Linea(f.get("producto", String.class), f.get("cantidad", Integer.class))).all())
                .concatMap(l -> db.sql("update existencias set cantidad = cantidad - :q where producto = :p and cantidad >= :q")
                        .bind("q", l.cantidad()).bind("p", l.producto()).fetch().rowsUpdated()
                        .flatMap(n -> n == 0 ? Mono.<Long>error(new SinExistencias(l.producto())) : Mono.just(n)))
                .then();
    }

    record Linea(String producto, int cantidad) {}
}
