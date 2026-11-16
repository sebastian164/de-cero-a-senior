package tienda.jpa;

import java.util.function.Supplier;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.aplicacion.puertos.salida.UnidadDeTrabajo;

@Component
public class UnidadDeTrabajoJpa implements UnidadDeTrabajo {

    private final TransactionTemplate transacciones;

    public UnidadDeTrabajoJpa(PlatformTransactionManager gestor) {
        this.transacciones = new TransactionTemplate(gestor);
    }

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        try {
            return transacciones.execute(estado -> trabajo.get());
        } catch (OptimisticLockingFailureException | PessimisticLockingFailureException conflicto) {
            // Versión vieja (sale al confirmar) o interbloqueo: para el caso de uso es lo mismo, otro se adelantó.
            throw new ConflictoDeConcurrencia("un pedido o sus existencias");
        }
    }
}
