package banco.cuentas;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

/** JDBC: cada método bloquea el hilo que lo llama hasta que PostgreSQL responde. */
@Repository
public class AlmacenJdbc {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacciones;

    public AlmacenJdbc(JdbcTemplate jdbc, TransactionTemplate transacciones) {
        this.jdbc = jdbc;
        this.transacciones = transacciones;
    }

    public Cuenta abrir(String titular, long centavos) {
        Cuenta cuenta = new Cuenta("cuenta-" + UUID.randomUUID(), titular, centavos, 1);
        jdbc.update("insert into cuenta (id, titular, saldo_centavos, version) values (?, ?, ?, ?)",
                cuenta.id(), cuenta.titular(), cuenta.saldoCentavos(), cuenta.version());
        return cuenta;
    }

    public Cuenta buscar(String id) {
        return jdbc.query("select id, titular, saldo_centavos, version from cuenta where id = ?",
                        (fila, n) -> new Cuenta(fila.getString("id"), fila.getString("titular"),
                                fila.getLong("saldo_centavos"), fila.getLong("version")), id)
                .stream().findFirst()
                .orElseThrow(() -> new CuentaNoEncontrada(id));
    }

    public Resumen resumen(String id) {
        // pg_sleep(0.2) hace de informe pesado: la consulta tarda 200 ms en la base
        return jdbc.query("""
                        select c.id, c.titular, c.saldo_centavos,
                               (select count(*) from movimiento m where m.cuenta_id = c.id) as movimientos
                        from cuenta c cross join pg_sleep(0.2)
                        where c.id = ?""",
                        (fila, n) -> new Resumen(fila.getString("id"), fila.getString("titular"),
                                fila.getLong("saldo_centavos"), fila.getLong("movimientos"), Thread.currentThread().getName()), id)
                .stream().findFirst()
                .orElseThrow(() -> new CuentaNoEncontrada(id));
    }

    /** Todo o nada, con la transacción de Spring atada al hilo que la ejecuta. */
    public void transferir(String origen, String destino, long centavos) {
        transacciones.executeWithoutResult(estado -> {
            int debitadas = jdbc.update("update cuenta set saldo_centavos = saldo_centavos - ?, version = version + 1 "
                    + "where id = ? and saldo_centavos >= ?", centavos, origen, centavos);
            if (debitadas == 0) {
                buscar(origen);                                   // lanza CuentaNoEncontrada si no existe
                throw new SaldoInsuficiente(origen);
            }
            int acreditadas = jdbc.update("update cuenta set saldo_centavos = saldo_centavos + ?, version = version + 1 "
                    + "where id = ?", centavos, destino);
            if (acreditadas == 0) {
                throw new CuentaNoEncontrada(destino);            // revierte también el débito
            }
            Timestamp ahora = Timestamp.from(Instant.now());
            String insertar = "insert into movimiento (cuenta_id, tipo, centavos, fecha, referencia) values (?, ?, ?, ?, ?)";
            jdbc.update(insertar, origen, "TRANSFERENCIA_ENVIADA", centavos, ahora, "a " + destino);
            jdbc.update(insertar, destino, "TRANSFERENCIA_RECIBIDA", centavos, ahora, "de " + origen);
        });
    }
}
