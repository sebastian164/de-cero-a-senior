package banco.jpa;

import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.puertos.salida.UnidadDeTrabajo;
import java.util.function.Supplier;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** El puerto de transacciones, sobre el gestor de transacciones de Spring. */
@Component
public class UnidadDeTrabajoJpa implements UnidadDeTrabajo {

    private final TransactionTemplate transacciones;

    public UnidadDeTrabajoJpa(PlatformTransactionManager gestor) {
        this.transacciones = new TransactionTemplate(gestor);
    }

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        try {
            // Abre la transacción (o se une a la que haya), ejecuta, y confirma. Si sale una excepción, revierte.
            return transacciones.execute(estado -> trabajo.get());
        } catch (OptimisticLockingFailureException conflicto) {
            // El UPDATE con "AND version = ?" no encontró la fila. Salta al confirmar, por eso se traduce aquí.
            throw new ConflictoDeConcurrencia("una cuenta");
        }
    }
}
