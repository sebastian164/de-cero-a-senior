package transacciones;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Regla: siempre debe quedar al menos un médico de guardia. */
@Service
public class Guardias {

    private final JdbcTemplate jdbc;

    public Guardias(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean pedirSalidaReadCommitted(String medico) {
        return pedirSalida(medico);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public boolean pedirSalidaSerializable(String medico) {
        return pedirSalida(medico);
    }

    private boolean pedirSalida(String medico) {
        Integer deGuardia = jdbc.queryForObject("SELECT count(*) FROM medicos WHERE de_guardia", Integer.class);
        if (deGuardia == null || deGuardia <= 1) {
            return false;                                   // rechazo de negocio: dejaría la guardia vacía
        }
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        jdbc.update("UPDATE medicos SET de_guardia = false WHERE nombre = ?", medico);
        return true;
    }
}
