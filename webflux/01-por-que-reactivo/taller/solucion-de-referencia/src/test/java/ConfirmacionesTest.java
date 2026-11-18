import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.Test;

class ConfirmacionesTest {
    @Test void lasTresVersionesDanElMismoResultado() throws Exception {
        List<String> a = Confirmaciones.conPool(100, 50, false, null);
        List<String> b = Confirmaciones.conVirtuales(100, false, null);
        List<String> c = Confirmaciones.conReactor(100, false, 0);
        assertEquals(a, b);
        assertEquals(a, c);
        assertEquals("pedido-1:CONFIRMADO", a.get(0));
    }

    @Test void conLimiteTambienCoinciden() throws Exception {
        assertEquals(Confirmaciones.conVirtuales(100, false, new Semaphore(20)), Confirmaciones.conReactor(100, false, 20));
    }

    @Test void lasConsultasIndependientesVanEnParalelo() {
        Confirmaciones.confirmarReactivo("calentar", false).block();
        long t0 = System.nanoTime();
        Confirmaciones.confirmarReactivo("pedido-1", false).block();
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(ms >= 155 && ms < 215, "debía rondar 160 ms y fue " + ms);   // 240 sería secuencial
    }
}
