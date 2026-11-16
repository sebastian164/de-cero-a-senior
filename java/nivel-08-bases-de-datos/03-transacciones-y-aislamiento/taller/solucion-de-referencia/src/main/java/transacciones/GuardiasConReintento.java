package transacciones;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * Paso 5: el reintento envuelve a la transacción, no al revés. Cada intento es una transacción nueva
 * (si el reintento estuviera dentro, repetiría sobre una transacción ya abortada).
 */
@Service
public class GuardiasConReintento {

    private final Guardias guardias;

    public GuardiasConReintento(Guardias guardias) {
        this.guardias = guardias;
    }

    @Retryable(includes = ConcurrencyFailureException.class, maxRetries = 10, delay = 20, jitter = 15, multiplier = 1.5)
    public boolean pedirSalida(String medico) {
        return guardias.pedirSalidaSerializable(medico);
    }
}
