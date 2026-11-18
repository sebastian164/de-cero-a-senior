package banco.adaptadores.salida.jdbc;

import banco.aplicacion.puertos.salida.UnidadDeTrabajo;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** El puerto de transacciones sobre JDBC. La transacción queda atada al hilo que llama: así funciona JDBC. */
@Component
public class UnidadDeTrabajoJdbc implements UnidadDeTrabajo {

    private final TransactionTemplate transacciones;

    public UnidadDeTrabajoJdbc(PlatformTransactionManager gestor) {
        this.transacciones = new TransactionTemplate(gestor);
    }

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        return transacciones.execute(estado -> trabajo.get());   // si ya hay una abierta en este hilo, se une a ella
    }
}
