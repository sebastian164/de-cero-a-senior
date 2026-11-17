package tienda.integrador.pagos.adaptadores.salida.jdbc;

import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tienda.integrador.pagos.aplicacion.Puertos.BandejaDeSalida;
import tienda.integrador.pagos.aplicacion.Puertos.RegistroDeRespuestas;
import tienda.integrador.pagos.aplicacion.Puertos.UnidadDeTrabajo;
import tienda.integrador.pagos.aplicacion.Puertos.Cobros;
import tienda.integrador.plataforma.PublicadorDeSalida;

/** Adaptador de salida sobre PostgreSQL: su propia base (esquema s07_pagos), la tabla de respuestas y la tabla de salida. */
public final class PagosJdbc implements RegistroDeRespuestas, BandejaDeSalida, UnidadDeTrabajo, Cobros {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public PagosJdbc(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
        jdbc.execute("CREATE TABLE IF NOT EXISTS respuestas(orden_id text PRIMARY KEY, tipo text NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS cobros(saga_id text PRIMARY KEY, centavos bigint, reembolsado boolean NOT NULL DEFAULT false)");
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
    public void cobrar(String sagaId, long centavos) {
        jdbc.update("INSERT INTO cobros(saga_id, centavos) VALUES (?, ?) ON CONFLICT (saga_id) DO NOTHING", sagaId, centavos);
    }

    @Override
    public boolean reembolsar(String sagaId) {
        return jdbc.update("UPDATE cobros SET reembolsado = true WHERE saga_id = ?", sagaId) > 0;
    }

    @Override
    public void ponerLapida(String sagaId) {
        jdbc.update("INSERT INTO lapidas(saga_id) VALUES (?) ON CONFLICT DO NOTHING", sagaId);
    }
}
