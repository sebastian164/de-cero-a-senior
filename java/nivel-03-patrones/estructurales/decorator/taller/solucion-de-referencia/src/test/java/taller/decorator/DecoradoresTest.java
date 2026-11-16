package taller.decorator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DecoradoresTest {

    @Test
    void cesarIdaYVuelta() {
        assertEquals("vhfuhwr", ConCifrado.cesar("secreto", 3));
        assertEquals("secreto", ConCifrado.cesar("vhfuhwr", 23));
    }

    @Test
    void ordenA_cacheFueraGuardaTextoPlano() {
        EnMemoria disco = new EnMemoria();
        ConCache cache = new ConCache(new ConCifrado(disco, 3));
        cache.escribir("k", "secreto");
        assertEquals("secreto", cache.leer("k"));
        assertEquals(Map.of("k", "vhfuhwr"), disco.crudo());
        assertEquals(Map.of("k", "secreto"), cache.contenido(), "el secreto queda en claro en memoria");
    }

    @Test
    void ordenB_cacheDentroGuardaCifrado() {
        EnMemoria disco = new EnMemoria();
        ConCache cache = new ConCache(disco);
        FuenteDatos f = new ConCifrado(cache, 3);
        f.escribir("k", "secreto");
        assertEquals("secreto", f.leer("k"));
        assertEquals(Map.of("k", "vhfuhwr"), cache.contenido());
    }

    @Test
    void laCacheEvitaLecturasYSeInvalidaAlEscribirYEliminar() {
        EnMemoria disco = new EnMemoria();
        FuenteDatos f = new ConCache(disco);
        f.escribir("k", "uno");
        f.leer("k"); f.leer("k"); f.leer("k");
        assertEquals(1, disco.lecturas());
        f.escribir("k", "dos");
        assertEquals("dos", f.leer("k"), "sin invalidar devolvería el valor viejo");
        f.eliminar("k");
        assertNull(f.leer("k"));
    }

    @Test
    void registroDeTiemposAnotaCadaOperacion() {
        List<String> lineas = new ArrayList<>();
        FuenteDatos f = new ConRegistroTiempos(new EnMemoria(), lineas::add);
        f.escribir("a", "1");
        f.leer("a");
        f.eliminar("a");
        assertEquals(3, lineas.size());
        assertTrue(lineas.get(0).startsWith("escribir(a) "));
        assertTrue(lineas.get(1).matches("leer\\(a\\) \\d+ µs"));
    }

    @Test
    void elCuartoMetodoTocoTodasLasImplementaciones() {
        for (Class<?> c : List.of(EnMemoria.class, ConCache.class, ConCifrado.class, ConRegistroTiempos.class)) {
            assertDoesNotThrow(() -> c.getDeclaredMethod("eliminar", String.class), c.getSimpleName());
        }
    }
}
