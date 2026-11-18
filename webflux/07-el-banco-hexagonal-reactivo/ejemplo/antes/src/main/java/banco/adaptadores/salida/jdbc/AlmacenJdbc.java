package banco.adaptadores.salida.jdbc;

import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.EventoDeCuenta;
import banco.aplicacion.puertos.salida.OrdenesProcesadas;
import banco.aplicacion.puertos.salida.PublicadorDeEventos;
import banco.aplicacion.puertos.salida.RegistroDeMovimientos;
import banco.aplicacion.puertos.salida.RepositorioDeCuentas;
import banco.dominio.Cuenta;
import banco.dominio.Dinero;
import banco.dominio.Movimiento;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Los puertos de salida del banco con SQL y JdbcTemplate. Usa la conexión de la transacción del hilo actual. */
@Repository
public class AlmacenJdbc implements RepositorioDeCuentas, RegistroDeMovimientos, OrdenesProcesadas, PublicadorDeEventos {

    private final JdbcTemplate jdbc;

    public AlmacenJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- RepositorioDeCuentas ----

    @Override
    public String siguienteId() {
        return "cuenta-" + UUID.randomUUID();
    }

    @Override
    public void guardar(Cuenta cuenta) {
        if (cuenta.version() == 0) {
            jdbc.update("insert into cuenta (id, titular, saldo_centavos, enviado_hoy_centavos, dia_del_tope, version) "
                            + "values (?, ?, ?, ?, ?, 1)",
                    cuenta.id(), cuenta.titular(), cuenta.saldo().centavos(), cuenta.enviadoHoy().centavos(), cuenta.diaDelTope());
            return;
        }
        int filas = jdbc.update("update cuenta set saldo_centavos = ?, enviado_hoy_centavos = ?, dia_del_tope = ?, "
                        + "version = version + 1 where id = ? and version = ?",
                cuenta.saldo().centavos(), cuenta.enviadoHoy().centavos(), cuenta.diaDelTope(), cuenta.id(), cuenta.version());
        if (filas == 0) {
            throw new ConflictoDeConcurrencia("la cuenta " + cuenta.id());
        }
    }

    @Override
    public Optional<Cuenta> buscar(String id) {
        return jdbc.query("select titular, saldo_centavos, enviado_hoy_centavos, dia_del_tope, version from cuenta where id = ?",
                (fila, n) -> Cuenta.existente(id, fila.getString("titular"), new Dinero(fila.getLong("saldo_centavos")),
                        new Dinero(fila.getLong("enviado_hoy_centavos")), fila.getObject("dia_del_tope", LocalDate.class),
                        fila.getLong("version")), id).stream().findFirst();
    }

    // ---- RegistroDeMovimientos ----

    @Override
    public void registrar(Movimiento movimiento) {
        jdbc.update("insert into movimiento (cuenta_id, tipo, centavos, fecha, referencia) values (?, ?, ?, ?, ?)",
                movimiento.cuentaId(), movimiento.tipo().name(), movimiento.valor().centavos(),
                enUtc(movimiento.fecha()), movimiento.referencia());
    }

    @Override
    public List<Movimiento> de(String cuentaId) {
        return jdbc.query("select tipo, centavos, fecha, referencia from movimiento where cuenta_id = ? order by id",
                (fila, n) -> new Movimiento(cuentaId, Movimiento.Tipo.valueOf(fila.getString("tipo")),
                        new Dinero(fila.getLong("centavos")), fila.getObject("fecha", OffsetDateTime.class).toInstant(),
                        fila.getString("referencia")), cuentaId);
    }

    // ---- OrdenesProcesadas ----

    @Override
    public boolean registrarSiEsNueva(String clave) {
        return jdbc.update("insert into orden_procesada (clave) values (?) on conflict do nothing", clave) == 1;
    }

    // ---- PublicadorDeEventos: la bandeja de salida ----

    @Override
    public void publicar(EventoDeCuenta evento) {
        jdbc.update("insert into evento_pendiente (id, tipo, cuenta_id, centavos, ocurrido_en) values (?, ?, ?, ?, ?)",
                evento.id(), evento.tipo(), evento.cuentaId(), evento.centavos(), enUtc(evento.ocurridoEn()));
    }

    private static OffsetDateTime enUtc(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }
}
