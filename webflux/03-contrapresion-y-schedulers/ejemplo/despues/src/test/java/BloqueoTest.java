import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.blockhound.BlockHound;
import reactor.blockhound.BlockingOperationError;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

/** BlockHound en las pruebas: así un bloqueo en el bucle de eventos rompe la construcción, no la producción. */
class BloqueoTest {

    @BeforeAll
    static void instalarBlockHound() {
        BlockHound.install();
    }

    @Test
    void elManejadorNoBloqueaElHiloQueLoAtiende() {
        StepVerifier.create(Mono.defer(() -> Main.consultarSaldo(1)).subscribeOn(Schedulers.parallel()))
                .expectNext("cuenta-1: 1000000")
                .verifyComplete();
    }

    @Test
    void llamarAJdbcDesdeUnHiloNoBloqueanteSeDetecta() {
        StepVerifier.create(Mono.fromCallable(() -> Main.consultarSaldoConJdbc("cuenta-1")).subscribeOn(Schedulers.parallel()))
                .expectError(BlockingOperationError.class)
                .verify();
    }
}
