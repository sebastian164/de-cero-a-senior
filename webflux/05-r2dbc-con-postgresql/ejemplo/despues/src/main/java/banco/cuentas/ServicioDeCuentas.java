package banco.cuentas;

import java.time.Instant;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** R2DBC: ningún método bloquea. Devuelven la promesa del resultado y el hilo sigue atendiendo a otros. */
@Service
public class ServicioDeCuentas {

    private final RepositorioDeCuentas cuentas;
    private final RepositorioDeMovimientos movimientos;
    private final DatabaseClient baseDeDatos;
    private final TransactionalOperator transacciones;

    public ServicioDeCuentas(RepositorioDeCuentas cuentas, RepositorioDeMovimientos movimientos,
                             DatabaseClient baseDeDatos, TransactionalOperator transacciones) {
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.baseDeDatos = baseDeDatos;
        this.transacciones = transacciones;
    }

    public Mono<Cuenta> abrir(String titular, long centavos) {
        return cuentas.save(new Cuenta("cuenta-" + UUID.randomUUID(), titular, centavos, null));   // version null: es nueva
    }

    public Mono<Cuenta> buscar(String id) {
        return cuentas.findById(id).switchIfEmpty(Mono.error(new CuentaNoEncontrada(id)));
    }

    public Flux<Movimiento> movimientosDe(String id) {
        return movimientos.findByCuentaIdOrderById(id);
    }

    /** SQL a mano con DatabaseClient, para lo que no es un CRUD. Misma consulta de 200 ms que con JDBC. */
    public Mono<Resumen> resumen(String id) {
        return baseDeDatos.sql("""
                        select c.id, c.titular, c.saldo_centavos,
                               (select count(*) from movimiento m where m.cuenta_id = c.id) as movimientos
                        from cuenta c cross join pg_sleep(0.2)
                        where c.id = :id""")
                .bind("id", id)
                .map(fila -> new Resumen(fila.get("id", String.class), fila.get("titular", String.class),
                        fila.get("saldo_centavos", Long.class), fila.get("movimientos", Long.class),
                        Thread.currentThread().getName()))
                .one()
                .switchIfEmpty(Mono.error(new CuentaNoEncontrada(id)));
    }

    /**
     * Todo o nada. La transacción no puede ir atada al hilo, porque la operación pasa por varios:
     * viaja en el contexto de Reactor, y TransactionalOperator la abre al suscribirse y la cierra al terminar.
     */
    public Mono<Void> transferir(String origenId, String destinoId, long centavos) {
        Mono<Void> trabajo = buscar(origenId)
                .flatMap(origen -> origen.saldoCentavos() < centavos
                        ? Mono.<Cuenta>error(new SaldoInsuficiente(origenId))
                        : cuentas.save(origen.conSaldo(origen.saldoCentavos() - centavos)))
                .then(buscar(destinoId))
                .flatMap(destino -> cuentas.save(destino.conSaldo(destino.saldoCentavos() + centavos)))
                .then(Mono.defer(() -> {
                    Instant ahora = Instant.now();
                    return movimientos.save(new Movimiento(null, origenId, "TRANSFERENCIA_ENVIADA", centavos, ahora, "a " + destinoId))
                            .then(movimientos.save(new Movimiento(null, destinoId, "TRANSFERENCIA_RECIBIDA", centavos, ahora, "de " + origenId)));
                }))
                .then();
        return transacciones.transactional(trabajo);
    }
}
