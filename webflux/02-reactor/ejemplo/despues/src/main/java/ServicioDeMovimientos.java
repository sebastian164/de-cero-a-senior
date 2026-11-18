import java.time.Duration;
import reactor.core.publisher.Mono;

/** Registra depósitos en dólares. Cada paso está encadenado: nada queda suelto. */
public final class ServicioDeMovimientos {

    static final Duration ESPERA_MAXIMA_TASA = Duration.ofSeconds(2);

    private final RepositorioDeMovimientos repositorio;
    private final ServicioDeTasas tasas;

    public ServicioDeMovimientos(RepositorioDeMovimientos repositorio, ServicioDeTasas tasas) {
        this.repositorio = repositorio;
        this.tasas = tasas;
    }

    public Mono<Movimiento> registrar(String cuentaId, long centavosDeDolar) {
        if (centavosDeDolar <= 0) {
            return Mono.error(new IllegalArgumentException("el valor debe ser mayor que cero"));   // una señal, no un throw
        }
        return tasas.enCache()
                .switchIfEmpty(tasas.consultarRemoto().timeout(ESPERA_MAXIMA_TASA))   // perezoso: solo si la caché está vacía
                .map(tasa -> new Movimiento(cuentaId, centavosDeDolar * tasa, "depósito en dólares"))
                .flatMap(repositorio::guardar);                    // el guardado es parte de la cadena
    }
}
