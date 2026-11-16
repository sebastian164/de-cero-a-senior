package tienda.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Entrega la bandeja de salida: el "correo" es el registro. Si el envío falla, la fila se queda para la siguiente vuelta. */
@Component
public class Mensajero {

    private static final Logger REGISTRO = LoggerFactory.getLogger(Mensajero.class);

    @PersistenceContext
    private EntityManager entidades;

    private final TransactionTemplate transacciones;

    public Mensajero(PlatformTransactionManager gestor) {
        this.transacciones = new TransactionTemplate(gestor);
    }

    @Scheduled(fixedDelay = 500)
    public void entregar() {
        transacciones.executeWithoutResult(estado -> {
            List<EventoPendienteJpa> pendientes = entidades.createNativeQuery(
                            "select * from evento_pendiente order by ocurrido_en limit 20 for update skip locked", EventoPendienteJpa.class)
                    .getResultList().stream().map(EventoPendienteJpa.class::cast).toList();
            for (EventoPendienteJpa fila : pendientes) {
                var evento = fila.aEvento();
                REGISTRO.info("[correo a {}] {} {}", evento.cliente(), evento.tipo(), evento.pedidoId());
                entidades.remove(fila);
            }
        });
    }
}
