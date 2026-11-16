package concurrencia;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * Paso 2: reintento automático (tres intentos en total) para operaciones idempotentes.
 *
 * <p>El reintento envuelve a la transacción, no al revés: esta clase solo reintenta y delega en {@link OperacionesTransaccionales},
 * un bean aparte cuyos métodos son transaccionales. Así cada intento abre una transacción nueva. Con {@code @Retryable} y
 * {@code @Transactional} en el mismo método el orden de los consejos sería implícito: el reintento no podría volver a empezar la
 * transacción si quedara por dentro de ella.
 */
@Service
public class ServicioConReintento {

    private final OperacionesTransaccionales operaciones;

    public ServicioConReintento(OperacionesTransaccionales operaciones) {
        this.operaciones = operaciones;
    }

    public AtomicInteger intentos() {
        return operaciones.intentos();
    }

    /** Identificador de la sesión de persistencia de cada intento: uno distinto por intento demuestra una transacción nueva. */
    public List<Integer> sesiones() {
        return operaciones.sesiones();
    }

    /**
     * "Fijar el precio en X" es idempotente: repetirla deja el mismo estado. Por eso se puede reintentar.
     * "Descontar 1" no lo es si el primer intento llegó a confirmar; aquí sí se reintenta porque un conflicto
     * de versión garantiza que el intento fallido no confirmó nada.
     */
    @Retryable(includes = ObjectOptimisticLockingFailureException.class, maxRetries = 2, delay = 10, jitter = 5)
    public void fijarPrecio(Long id, BigDecimal precio) {
        operaciones.fijarPrecio(id, precio);
    }

    @Retryable(includes = ObjectOptimisticLockingFailureException.class, maxRetries = 2, delay = 10, jitter = 5)
    public void venderOptimistaConReintento(Long id) {
        operaciones.vender(id);
    }
}
