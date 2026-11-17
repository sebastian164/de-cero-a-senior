package tienda;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tienda.Mensajes.Orden;

/** Programa despachos. A esta ciudad no llega ninguna transportadora. */
public class Envios extends Participante {

    public static final String CIUDAD_IMPOSIBLE = "Atlantis";

    public Envios(JdbcTemplate jdbc, TransactionTemplate tx) {
        super(jdbc, tx);
    }

    @Override
    protected String ejecutar(Orden o) {
        if (!o.tipo().equals("programar-envio")) throw new IllegalArgumentException("orden desconocida: " + o.tipo());
        if (CIUDAD_IMPOSIBLE.equals(o.ciudad())) return "envio-imposible";
        jdbc.update("INSERT INTO despachos(saga_id, ciudad) VALUES (?, ?) ON CONFLICT (saga_id) DO NOTHING", o.sagaId(), o.ciudad());
        return "envio-programado";
    }
}
