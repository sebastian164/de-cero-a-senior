package tienda.eventos;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Núcleo compartido: guarda el evento en la MISMA transacción que el cambio de negocio. */
@Service
public class Outbox {
    private final JdbcClient jdbc;

    public Outbox(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publicar(String tipo, String clave, String carga) {
        jdbc.sql("INSERT INTO eventos_outbox (tipo, clave, carga) VALUES (?,?,?)").params(tipo, clave, carga).update();
    }
}
