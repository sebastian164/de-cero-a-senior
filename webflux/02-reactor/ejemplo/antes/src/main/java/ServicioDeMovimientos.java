import reactor.core.publisher.Mono;

/** Registra depósitos en dólares. Compila, devuelve lo esperado... y tiene dos errores. */
public final class ServicioDeMovimientos {

    private final RepositorioDeMovimientos repositorio;
    private final ServicioDeTasas tasas;

    public ServicioDeMovimientos(RepositorioDeMovimientos repositorio, ServicioDeTasas tasas) {
        this.repositorio = repositorio;
        this.tasas = tasas;
    }

    public Mono<Movimiento> registrar(String cuentaId, long centavosDeDolar) {
        return tasas.enCache()
                .switchIfEmpty(Mono.just(tasas.consultarRemoto()))    // 1. se llama al ensamblar, haya caché o no
                .map(tasa -> new Movimiento(cuentaId, centavosDeDolar * tasa, "depósito en dólares"))
                .doOnNext(movimiento -> repositorio.guardar(movimiento));   // 2. crea el Mono del guardado y lo tira
    }
}
