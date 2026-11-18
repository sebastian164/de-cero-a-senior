package pedidos;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.blockhound.BlockHound;
import reactor.blockhound.BlockingOperationError;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

class BloqueoEnElBucleTest {
    static Scheduler bucle;

    @BeforeAll static void preparar() { BlockHound.install(); bucle = Schedulers.newParallel("bucle", 2); }
    @AfterAll static void cerrar() { bucle.dispose(); }

    /** Estado elegido con -Dpagos=apurado (antes de corregir) o corregido (por omisión). */
    static Pagos pagos() { return "apurado".equals(System.getProperty("pagos")) ? new PagosApurados() : new PagosCorregidos(); }

    @Test void confirmarConLaSuscripcionEnElBucleNoBloquea() {
        var almacen = new Dobles2.Almacen(1);
        var s = Medicion.servicio(bucle, pagos(), almacen);
        StepVerifier.create(s.confirmar("p1").subscribeOn(bucle))
                .expectNextMatches(p -> p.estado().equals("CONFIRMADO"))
                .expectComplete().verify(Duration.ofSeconds(10));
    }

    @Test void blockHoundDetectaElMetodoBloqueante() {
        var almacen = new Dobles2.Almacen(1);
        var s = Medicion.servicio(bucle, new PagosApurados(), almacen);
        StepVerifier.create(s.confirmar("p1").subscribeOn(bucle)).expectErrorSatisfies(e -> {
            assertInstanceOf(BlockingOperationError.class, e);
            System.out.println("MENSAJE DE BLOCKHOUND: " + e.getMessage());
        }).verify(Duration.ofSeconds(10));
    }
}
