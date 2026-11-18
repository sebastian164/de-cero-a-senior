import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ServicioDeMovimientosTest {

    private final RepositorioDeMovimientos repositorio = new RepositorioDeMovimientos();
    private final ServicioDeTasas tasas = new ServicioDeTasas(Duration.ofMillis(10));
    private final ServicioDeMovimientos servicio = new ServicioDeMovimientos(repositorio, tasas);

    @Test
    void sinSuscripcionNoPasaNada() {
        servicio.registrar("cuenta-1", 10_000);                    // ensamblado y descartado

        assertEquals(0, tasas.llamadasRemotas());
        assertEquals(0, repositorio.cuantos());
    }

    @Test
    void registraConvierteYGuarda() {
        StepVerifier.create(servicio.registrar("cuenta-1", 10_000))
                .expectNext(new Movimiento("cuenta-1", 40_000_000, "depósito en dólares"))
                .verifyComplete();

        StepVerifier.create(repositorio.todos())
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void conLaCacheLlenaNoVuelveALlamar() {
        StepVerifier.create(servicio.registrar("cuenta-1", 100).then(servicio.registrar("cuenta-2", 100)))
                .expectNextCount(1)
                .verifyComplete();

        assertEquals(1, tasas.llamadasRemotas());
    }

    @Test
    void unValorCeroEsUnaSenalDeError() {
        StepVerifier.create(servicio.registrar("cuenta-1", 0))
                .expectErrorMessage("el valor debe ser mayor que cero")
                .verify();
    }

    @Test
    void siLasTasasTardanMasDeDosSegundosSeCorta() {
        // Tiempo virtual: la prueba dura milisegundos aunque "pasen" dos segundos.
        StepVerifier.withVirtualTime(() -> new ServicioDeMovimientos(repositorio, new ServicioDeTasas(Duration.ofMinutes(1)))
                        .registrar("cuenta-1", 10_000))
                .expectSubscription()
                .expectNoEvent(Duration.ofMillis(1_999))
                .thenAwait(Duration.ofMillis(1))
                .expectError(TimeoutException.class)
                .verify(Duration.ofSeconds(1));

        assertEquals(0, repositorio.cuantos());
    }
}
