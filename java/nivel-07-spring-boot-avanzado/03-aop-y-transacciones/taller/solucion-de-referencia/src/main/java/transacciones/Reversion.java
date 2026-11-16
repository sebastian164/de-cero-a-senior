package transacciones;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Para UnexpectedRollbackException y para comparar REQUIRED con REQUIRES_NEW. */
@Service
public class Reversion {

    @Service
    public static class Interno {
        private final JdbcClient jdbc;
        public Interno(JdbcClient jdbc) { this.jdbc = jdbc; }

        @Transactional                                         // REQUIRED: se une a la del llamador
        public void fallar() {
            jdbc.sql("INSERT INTO n703_pedidos(cliente, total) VALUES ('interno', 1)").update();
            throw new IllegalStateException("falla interna");  // marca la transacción COMPARTIDA para revertir
        }

        @Transactional(propagation = Propagation.REQUIRED)
        public void auditarRequired(String accion, String resultado) {
            jdbc.sql("INSERT INTO n703_auditoria(accion, resultado) VALUES (?, ?)").params(accion, resultado).update();
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void auditarRequiresNew(String accion, String resultado) {
            jdbc.sql("INSERT INTO n703_auditoria(accion, resultado) VALUES (?, ?)").params(accion, resultado).update();
        }
    }

    private final Interno interno;
    private final JdbcClient jdbc;
    public Reversion(Interno interno, JdbcClient jdbc) { this.interno = interno; this.jdbc = jdbc; }

    @Transactional
    public void externoQueCaptura() {
        jdbc.sql("INSERT INTO n703_pedidos(cliente, total) VALUES ('externo', 1)").update();
        try {
            interno.fallar();
        } catch (IllegalStateException e) {
            // "lo manejo y sigo": pero la transacción ya quedó marcada rollback-only
        }
    }                                                          // al confirmar: UnexpectedRollbackException

    @Transactional
    public void operarConAuditoria(boolean requiresNew) {
        if (requiresNew) interno.auditarRequiresNew("transferir", "intento-new");
        else interno.auditarRequired("transferir", "intento-required");
        jdbc.sql("INSERT INTO n703_pedidos(cliente, total) VALUES ('op', 1)").update();
        throw new IllegalStateException("la operación falla");
    }
}
