package pedidos;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pedidos.Dobles.*;
import reactor.test.StepVerifier;

class ConfirmarPedidoTest {
    PedidosEnMemoria pedidos = new PedidosEnMemoria();

    @BeforeEach void datos() { pedidos.datos.put("p1", new Pedido("p1", 500, "PENDIENTE")); }

    ConfirmarPedido servicio(InventarioEnMemoria i, AntifraudeEnMemoria a, PagosEnMemoria p) { return new ConfirmarPedido(pedidos, i, a, p); }
    Duration ms(long n) { return Duration.ofMillis(n); }

    @Test void casoFeliz() {
        var pagos = new PagosEnMemoria(ms(20));
        var s = servicio(new InventarioEnMemoria(ms(20)), new AntifraudeEnMemoria(ms(20), true), pagos);
        StepVerifier.create(s.confirmar("p1")).expectNextMatches(p -> p.estado().equals("CONFIRMADO")).verifyComplete();
        assertEquals(1, pagos.cobros.get());
        assertEquals("CONFIRMADO", pedidos.datos.get("p1").estado());
    }

    @Test void pedidoInexistente() {
        var pagos = new PagosEnMemoria(ms(20));
        var s = servicio(new InventarioEnMemoria(ms(20)), new AntifraudeEnMemoria(ms(20), true), pagos);
        StepVerifier.create(s.confirmar("no-existe")).expectError(PedidoNoEncontrado.class).verify();
        assertEquals(0, pagos.cobros.get());
    }

    @Test void antifraudeRechazaYNoSeCobra() {
        var pagos = new PagosEnMemoria(ms(20));
        var s = servicio(new InventarioEnMemoria(ms(20)), new AntifraudeEnMemoria(ms(20), false), pagos);
        StepVerifier.create(s.confirmar("p1")).expectError(RechazadoPorAntifraude.class).verify();
        assertEquals(0, pagos.cobros.get(), "el cobro no debía llamarse");
        assertEquals(0, pedidos.guardados.get());
    }

    @Test void cobroLentoFallaConMiExcepcionYConTiempoVirtual() {
        var pagos = new PagosEnMemoria(Duration.ofSeconds(10));
        var s = servicio(new InventarioEnMemoria(ms(100)), new AntifraudeEnMemoria(ms(100), true), pagos);
        long t0 = System.nanoTime();
        StepVerifier.withVirtualTime(() -> s.confirmar("p1"))
                .thenAwait(Duration.ofSeconds(4))
                .expectError(PagoDemasiadoLento.class)
                .verify();
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(ms < 1000, "con tiempo virtual debe durar menos de 1 s y duró " + ms);
        assertEquals(0, pedidos.guardados.get());
    }

    @Test void sinSuscribirNoSeReservaNiSeCobraNada() {
        var inv = new InventarioEnMemoria(ms(20)); var af = new AntifraudeEnMemoria(ms(20), true); var pagos = new PagosEnMemoria(ms(20));
        var cadena = servicio(inv, af, pagos).confirmar("p1");    // solo se arma
        assertNotNull(cadena);
        assertEquals(0, inv.llamadas.get());
        assertEquals(0, af.llamadas.get());
        assertEquals(0, pagos.cobros.get());
        assertEquals(0, pedidos.guardados.get());
        assertEquals("PENDIENTE", pedidos.datos.get("p1").estado());
    }

    @Test void reservaYAntifraudeVanEnParaleloYElCobroEsDespues() {
        var pagos = new PagosEnMemoria(ms(50));
        var s = servicio(new InventarioEnMemoria(ms(300)), new AntifraudeEnMemoria(ms(300), true), pagos);
        long t0 = System.nanoTime();
        s.confirmar("p1").block(Duration.ofSeconds(5));
        long total = (System.nanoTime() - t0) / 1_000_000;
        // en paralelo: ~300 + 50 + 10 = 360; en secuencia serían ~660
        assertTrue(total >= 350 && total < 550, "total " + total + " ms");
    }
}
