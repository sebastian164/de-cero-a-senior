package taller.memento;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class MementoTest {

    @Test
    void deshacerYRehacer() {
        Configurador pc = new Configurador();
        Historial h = new Historial(pc, 20);
        h.cambiar(() -> pc.procesador("Ryzen 7"));
        h.cambiar(() -> pc.memoria(32));
        assertEquals("Ryzen 7, 32 GB, SSD 256, []", pc.toString());
        h.deshacer();
        assertEquals("Ryzen 7, 8 GB, SSD 256, []", pc.toString());
        h.deshacer();
        assertEquals("Core i3, 8 GB, SSD 256, []", pc.toString());
        assertFalse(h.deshacer());
        h.rehacer();
        h.rehacer();
        assertEquals("Ryzen 7, 32 GB, SSD 256, []", pc.toString());
        assertFalse(h.rehacer());
    }

    @Test
    void laListaDeAccesoriosSeCopia() {
        Configurador pc = new Configurador();
        Historial h = new Historial(pc, 20);
        h.cambiar(() -> pc.agregarAccesorio("Teclado"));
        h.cambiar(() -> pc.agregarAccesorio("Mouse"));
        h.deshacer();
        assertEquals("Core i3, 8 GB, SSD 256, [Teclado]", pc.toString(),
                "si el memento compartiera la lista, deshacer no quitaría el mouse");
        pc.agregarAccesorio("Monitor");   // cambio sin historial
        h.rehacer();
        assertEquals("Core i3, 8 GB, SSD 256, [Teclado, Mouse]", pc.toString());
    }

    @Test
    void historialLimitadoA20() {
        Configurador pc = new Configurador();
        Historial h = new Historial(pc, 20);
        for (int i = 1; i <= 25; i++) {
            int gb = i;
            h.cambiar(() -> pc.memoria(gb));
        }
        assertEquals(20, h.instantaneas());
        int deshechos = 0;
        while (h.deshacer()) deshechos++;
        assertEquals(20, deshechos);
        assertEquals("Core i3, 5 GB, SSD 256, []", pc.toString(), "las 5 más viejas se descartaron");
    }

    @Test
    void elMementoEsOpaco() {
        Configurador.Memento m = new Configurador().guardar();
        assertEquals(0, Configurador.Memento.class.getDeclaredMethods().length);
        assertFalse(java.lang.reflect.Modifier.isPublic(m.getClass().getModifiers()));
    }

    @Test
    void versionInmutable() {
        HistorialInmutable h = new HistorialInmutable(Configuracion.BASE, 20);
        h.aplicar(c -> c.conAccesorio("Teclado"));
        Configuracion conTeclado = h.actual();
        h.aplicar(c -> c.conAccesorio("Mouse"));
        assertEquals(List.of("Teclado"), conTeclado.accesorios(), "las versiones viejas no cambian");
        h.deshacer();
        assertSame(conTeclado, h.actual());
        assertThrows(UnsupportedOperationException.class, () -> h.actual().accesorios().add("x"));
        for (int i = 0; i < 30; i++) h.aplicar(c -> c.conMemoria(16));
        assertEquals(20, h.instantaneas());
    }
}
