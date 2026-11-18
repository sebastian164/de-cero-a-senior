package banco.adaptadores.salida.r2dbc;

import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.EventoDeCuenta;
import banco.aplicacion.reactiva.puertos.salida.OrdenesProcesadas;
import banco.aplicacion.reactiva.puertos.salida.PublicadorDeEventos;
import banco.aplicacion.reactiva.puertos.salida.RegistroDeMovimientos;
import banco.aplicacion.reactiva.puertos.salida.RepositorioDeCuentas;
import banco.dominio.Cuenta;
import banco.dominio.Dinero;
import banco.dominio.Movimiento;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Los puertos de salida con SQL y DatabaseClient. El mismo SQL que el adaptador JDBC; otra forma de esperarlo. */
@Repository
public class AlmacenR2dbc implements RepositorioDeCuentas, RegistroDeMovimientos, OrdenesProcesadas, PublicadorDeEventos {

    private final DatabaseClient db;

    public AlmacenR2dbc(DatabaseClient db) {
        this.db = db;
    }

    // ---- RepositorioDeCuentas ----

    @Override
    public String siguienteId() {
        return "cuenta-" + UUID.randomUUID();
    }

    @Override
    public Mono<Void> guardar(Cuenta cuenta) {
        if (cuenta.version() == 0) {
            return db.sql("insert into cuenta (id, titular, saldo_centavos, enviado_hoy_centavos, dia_del_tope, version) "
                            + "values (:id, :titular, :saldo, :enviado, :dia, 1)")
                    .bind("id", cuenta.id())
                    .bind("titular", cuenta.titular())
                    .bind("saldo", cuenta.saldo().centavos())
                    .bind("enviado", cuenta.enviadoHoy().centavos())
                    .bind("dia", cuenta.diaDelTope())
                    .then();
        }
        return db.sql("update cuenta set saldo_centavos = :saldo, enviado_hoy_centavos = :enviado, dia_del_tope = :dia, "
                        + "version = version + 1 where id = :id and version = :version")
                .bind("saldo", cuenta.saldo().centavos())
                .bind("enviado", cuenta.enviadoHoy().centavos())
                .bind("dia", cuenta.diaDelTope())
                .bind("id", cuenta.id())
                .bind("version", cuenta.version())
                .fetch().rowsUpdated()
                .flatMap(filas -> filas == 0
                        ? Mono.error(new ConflictoDeConcurrencia("la cuenta " + cuenta.id()))
                        : Mono.empty());
    }

    @Override
    public Mono<Cuenta> buscar(String id) {
        return db.sql("select titular, saldo_centavos, enviado_hoy_centavos, dia_del_tope, version from cuenta where id = :id")
                .bind("id", id)
                .map(fila -> Cuenta.existente(id, fila.get("titular", String.class),
                        new Dinero(fila.get("saldo_centavos", Long.class)), new Dinero(fila.get("enviado_hoy_centavos", Long.class)),
                        fila.get("dia_del_tope", LocalDate.class), fila.get("version", Long.class)))
                .one();
    }

    // ---- RegistroDeMovimientos ----

    @Override
    public Mono<Void> registrar(Movimiento movimiento) {
        return db.sql("insert into movimiento (cuenta_id, tipo, centavos, fecha, referencia) "
                        + "values (:cuenta, :tipo, :centavos, :fecha, :referencia)")
                .bind("cuenta", movimiento.cuentaId())
                .bind("tipo", movimiento.tipo().name())
                .bind("centavos", movimiento.valor().centavos())
                .bind("fecha", movimiento.fecha())
                .bind("referencia", movimiento.referencia())
                .then();
    }

    @Override
    public Flux<Movimiento> de(String cuentaId) {
        return db.sql("select tipo, centavos, fecha, referencia from movimiento where cuenta_id = :cuenta order by id")
                .bind("cuenta", cuentaId)
                .map(fila -> new Movimiento(cuentaId, Movimiento.Tipo.valueOf(fila.get("tipo", String.class)),
                        new Dinero(fila.get("centavos", Long.class)), fila.get("fecha", Instant.class),
                        fila.get("referencia", String.class)))
                .all();
    }

    // ---- OrdenesProcesadas ----

    @Override
    public Mono<Boolean> registrarSiEsNueva(String clave) {
        return db.sql("insert into orden_procesada (clave) values (:clave) on conflict do nothing")
                .bind("clave", clave)
                .fetch().rowsUpdated()
                .map(filas -> filas == 1);
    }

    // ---- PublicadorDeEventos: la bandeja de salida ----

    @Override
    public Mono<Void> publicar(EventoDeCuenta evento) {
        return db.sql("insert into evento_pendiente (id, tipo, cuenta_id, centavos, ocurrido_en) "
                        + "values (:id, :tipo, :cuenta, :centavos, :cuando)")
                .bind("id", evento.id())
                .bind("tipo", evento.tipo())
                .bind("cuenta", evento.cuentaId())
                .bind("centavos", evento.centavos())
                .bind("cuando", evento.ocurridoEn())
                .then();
    }
}
