import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Un repositorio reactivo en memoria. Está bien hecho: guardar no hace nada hasta que alguien se suscribe. */
public final class RepositorioDeMovimientos {

    private final List<Movimiento> guardados = new CopyOnWriteArrayList<>();

    public Mono<Movimiento> guardar(Movimiento movimiento) {
        return Mono.fromCallable(() -> {
            guardados.add(movimiento);
            return movimiento;
        });
    }

    public Flux<Movimiento> todos() {
        return Flux.fromIterable(guardados);
    }

    public int cuantos() {
        return guardados.size();
    }
}
