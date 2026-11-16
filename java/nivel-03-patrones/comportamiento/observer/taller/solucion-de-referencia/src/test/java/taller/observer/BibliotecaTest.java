package taller.observer;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.ref.WeakReference;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BibliotecaTest {

    List<String> salida = new ArrayList<>();
    Biblioteca b;
    Estadisticas est = new Estadisticas();

    @BeforeEach
    void armar() {
        b = new Biblioteca(salida::add);
        b.prestar("Cien años", "ana", LocalDate.of(2025, 5, 1));
        b.ponerEnEspera("Cien años", "luis");
        b.suscribir(new AvisoListaEspera(salida::add));
        b.suscribir(new CalculoMulta(salida::add));
        b.suscribir(est);
    }

    @Test
    void lasCuatroConsecuencias() {
        b.devolver("Cien años", LocalDate.of(2025, 5, 20));
        assertTrue(b.disponible("Cien años"));
        assertEquals(List.of("Aviso a luis: 'Cien años' ya está disponible", "Multa a ana: $4000 por 4 días"), salida);
        assertEquals(1, est.lecturasPorLibro.get("Cien años"));
    }

    @Test
    void sinRetrasoNoHayMulta() {
        b.devolver("Cien años", LocalDate.of(2025, 5, 16));
        assertEquals(List.of("Aviso a luis: 'Cien años' ya está disponible"), salida);
    }

    @Test
    void unOyenteQueFallaNoAfectaALosDemasNiALaDevolucion() {
        Biblioteca otra = new Biblioteca(salida::add);
        Estadisticas e = new Estadisticas();
        otra.prestar("L", "ana", LocalDate.of(2025, 5, 1));
        otra.suscribir(d -> { throw new IllegalStateException("caído"); });
        otra.suscribir(e);
        otra.devolver("L", LocalDate.of(2025, 5, 2));
        assertTrue(otra.disponible("L"));
        assertEquals(1, e.lecturasPorLibro.get("L"));
        assertEquals(1, salida.size());
        assertTrue(salida.get(0).startsWith("ERROR en oyente"));
        assertTrue(salida.get(0).endsWith(": caído"));
    }

    @Test
    void siElAvisoFallaLaColaYaAvanzoDeFormaCoherente() {
        Biblioteca otra = new Biblioteca(salida::add);
        otra.prestar("L", "ana", LocalDate.of(2025, 5, 1));
        otra.ponerEnEspera("L", "luis");
        otra.ponerEnEspera("L", "eva");
        List<Devolucion> vistos = new ArrayList<>();
        otra.suscribir(new AvisoListaEspera(m -> { throw new IllegalStateException("correo caído"); }));
        otra.suscribir(vistos::add);
        otra.devolver("L", LocalDate.of(2025, 5, 2));
        // La operación avanzó la cola (luis es el siguiente) aunque el aviso falló: la política registra el error
        // y el reintento del aviso se hace con el dato que lleva el evento, no volviendo a sacar de la cola.
        assertEquals(List.of("eva"), otra.enEspera("L"));
        assertEquals(java.util.Optional.of("luis"), vistos.get(0).siguienteEnEspera());
        assertEquals(List.of("ERROR en oyente AvisoListaEspera: correo caído"), salida);
    }

    @Test
    void sinOyenteDeAvisoLaColaIgualAvanza() {
        Biblioteca otra = new Biblioteca(salida::add);
        otra.prestar("L", "ana", LocalDate.of(2025, 5, 1));
        otra.ponerEnEspera("L", "luis");
        otra.devolver("L", LocalDate.of(2025, 5, 2));
        assertEquals(List.of(), otra.enEspera("L"), "avanzar la cola no depende de que haya oyentes");
    }

    @Test
    void desuscribir() {
        b.desuscribir(est);
        b.devolver("Cien años", LocalDate.of(2025, 5, 2));
        assertNull(est.lecturasPorLibro.get("Cien años"));
    }

    @Test
    void sinDesuscribirElOyenteNoSePuedeRecolectar() throws Exception {
        Biblioteca larga = new Biblioteca(s -> { });
        Fuga.PanelPrestamos panel = new Fuga.PanelPrestamos();
        WeakReference<Fuga.PanelPrestamos> ref = new WeakReference<>(panel);
        larga.suscribir(panel);
        panel = null;
        gc();
        assertNotNull(ref.get(), "la biblioteca lo mantiene vivo: fuga");

        larga.desuscribir(ref.get());
        gc();
        assertNull(ref.get(), "desuscrito, el recolector lo libera");
    }

    @Test
    void laVersionDirectaProduceLoMismo() {
        assertEquals("disponible=true [Aviso a luis: 'L' ya está disponible] [Multa a ana: $4000 por 4 días] lecturas={L=1}",
                BibliotecaDirecta.demostrar());
    }

    @Test
    void demostracionDeFuga() {
        assertEquals("50 paneles (~50 MB) retenidos; desuscribiendo: 0", Fuga.demostrar());
    }

    private static void gc() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            System.gc();
            Thread.sleep(20);
        }
    }
}
