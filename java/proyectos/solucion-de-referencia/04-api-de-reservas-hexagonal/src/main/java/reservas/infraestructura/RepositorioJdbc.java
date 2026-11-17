package reservas.infraestructura;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import reservas.aplicacion.Puertos.*;
import reservas.dominio.Franja;
import reservas.dominio.Reserva;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RepositorioJdbc implements RepositorioReservas {
    private final JdbcClient jdbc;

    public RepositorioJdbc(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public void guardar(Reserva r) {
        try {
            jdbc.sql("INSERT INTO reservas (id, espacio, usuario, inicio, fin, estado) VALUES (?,?,?,?,?,?)")
                    .params(r.id(), r.espacio(), r.usuario(), Timestamp.from(r.franja().inicio()), Timestamp.from(r.franja().fin()), r.estado().name())
                    .update();
        } catch (DataIntegrityViolationException e) {
            if (e.getRootCause() instanceof SQLException s && "23P01".equals(s.getSQLState())) throw new Reserva.FranjaOcupada(r.espacio());
            throw e;
        }
    }

    @Override
    public void actualizar(Reserva r) {
        jdbc.sql("UPDATE reservas SET estado = ? WHERE id = ?").params(r.estado().name(), r.id()).update();
    }

    @Override
    public Optional<Reserva> buscar(UUID id) {
        return jdbc.sql("SELECT * FROM reservas WHERE id = ?").param(id).query(RepositorioJdbc::mapear).optional();
    }

    @Override
    public java.util.List<Franja> ocupadas(String espacio, Instant desde, Instant hasta) {
        return jdbc.sql("SELECT inicio, fin FROM reservas WHERE espacio = ? AND estado = 'ACTIVA' AND inicio < ? AND fin > ? ORDER BY inicio")
                .params(espacio, Timestamp.from(hasta), Timestamp.from(desde))
                .query((rs, n) -> new Franja(rs.getTimestamp("inicio").toInstant(), rs.getTimestamp("fin").toInstant())).list();
    }

    @Override
    public Pagina<Reserva> listar(String usuario, int pagina, int tamano) {
        var filas = jdbc.sql("SELECT * FROM reservas WHERE (?::text IS NULL OR usuario = ?) ORDER BY inicio, id LIMIT ? OFFSET ?")
                .params(usuario, usuario, tamano, (long) pagina * tamano).query(RepositorioJdbc::mapear).list();
        long total = jdbc.sql("SELECT count(*) FROM reservas WHERE (?::text IS NULL OR usuario = ?)").params(usuario, usuario).query(Long.class).single();
        return new Pagina<>(filas, pagina, tamano, total);
    }

    @Override
    public Optional<Reserva> porClave(String clave, String usuario) {
        return jdbc.sql("SELECT r.* FROM reservas r JOIN claves_idempotencia c ON c.reserva_id = r.id WHERE c.clave = ? AND c.usuario = ?")
                .params(clave, usuario).query(RepositorioJdbc::mapear).optional();
    }

    @Override
    public void asociarClave(String clave, String usuario, UUID reserva) {
        jdbc.sql("INSERT INTO claves_idempotencia (clave, usuario, reserva_id) VALUES (?,?,?)").params(clave, usuario, reserva).update();
    }

    @Override
    public void esperar(String espacio, String usuario, Franja f) {
        jdbc.sql("INSERT INTO lista_espera (espacio, usuario, inicio, fin) VALUES (?,?,?,?)")
                .params(espacio, usuario, Timestamp.from(f.inicio()), Timestamp.from(f.fin())).update();
    }

    @Override
    public Optional<Espera> primeroEnEspera(String espacio, Franja f) {
        return jdbc.sql("SELECT * FROM lista_espera WHERE espacio = ? AND inicio = ? AND fin = ? ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED")
                .params(espacio, Timestamp.from(f.inicio()), Timestamp.from(f.fin()))
                .query((rs, n) -> new Espera(rs.getString("espacio"), rs.getString("usuario"),
                        new Franja(rs.getTimestamp("inicio").toInstant(), rs.getTimestamp("fin").toInstant()), rs.getLong("id")))
                .optional();
    }

    @Override
    public void quitarDeEspera(long id) {
        jdbc.sql("DELETE FROM lista_espera WHERE id = ?").param(id).update();
    }

    private static Reserva mapear(java.sql.ResultSet rs, int n) throws SQLException {
        Instant ini = rs.getTimestamp("inicio").toInstant(), fin = rs.getTimestamp("fin").toInstant();
        return new Reserva(rs.getObject("id", UUID.class), rs.getString("espacio"), rs.getString("usuario"), new Franja(ini, fin),
                Reserva.Estado.valueOf(rs.getString("estado")));
    }
}
