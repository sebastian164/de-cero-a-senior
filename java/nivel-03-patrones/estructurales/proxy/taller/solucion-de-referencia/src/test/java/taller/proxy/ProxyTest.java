package taller.proxy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProxyTest {

    @Test
    void cachePorAnioFiltraDatosEntreUsuarios() {
        Reportes r = new ReportesConPermisos(new ReportesConCachePorAnio(new ReportesPorRegion()), Set.of("ana", "luis"));
        assertEquals("Ventas 2025 región Boyacá", r.ventasDelAnio("ana", 2025));
        assertEquals("Ventas 2025 región Boyacá", r.ventasDelAnio("luis", 2025), "el error de seguridad");
    }

    @Test
    void cacheConClaveCompletaEsCorrecta() {
        ReportesPorRegion real = new ReportesPorRegion();
        Reportes r = new ReportesConPermisos(new ReportesConCache(real), Set.of("ana", "luis"));
        assertEquals("Ventas 2025 región Boyacá", r.ventasDelAnio("ana", 2025));
        assertEquals("Ventas 2025 región Antioquia", r.ventasDelAnio("luis", 2025));
        r.ventasDelAnio("ana", 2025);
        assertEquals(2, real.calculos);
    }

    @Test
    void cacheFueraDePermisosDejaPasarAlIntruso() {
        assertEquals("Ventas 2025 región Boyacá", Demostraciones.intrusoConCacheFuera());
        assertEquals("denegado", Demostraciones.intrusoConCacheCorregidaFuera());
    }

    @Test
    void cargaDiferidaSoloEnElPrimerMostrar() {
        ImagenReal.cargas = 0;
        List<String> salida = new ArrayList<>();
        Imagen img = new ImagenDiferida("a.png", salida::add);
        assertEquals(0, ImagenReal.cargas);
        img.mostrar();
        img.mostrar();
        assertEquals(1, ImagenReal.cargas);
        assertEquals(List.of("  cargando a.png del disco (costoso)", "  mostrando a.png", "  mostrando a.png"), salida);
    }

    @Test
    void proxyDinamicoMideCualquierInterfaz() {
        List<String> salida = new ArrayList<>();
        Reportes r = Cronometro.envolver(Reportes.class, new ReportesPorRegion(), salida::add);
        assertEquals("Ventas 2024 región Boyacá", r.ventasDelAnio("ana", 2024));
        Runnable tarea = Cronometro.envolver(Runnable.class, () -> { }, salida::add);
        tarea.run();
        assertEquals(2, salida.size());
        assertTrue(salida.get(0).matches("  Reportes\\.ventasDelAnio tardó \\d+ µs"));
        assertTrue(salida.get(1).startsWith("  Runnable.run tardó"));
    }

    @Test
    void proxyDinamicoPropagaLaExcepcionOriginal() {
        Reportes r = Cronometro.envolver(Reportes.class,
                new ReportesConPermisos(new ReportesPorRegion(), Set.of()), s -> { });
        assertThrows(SecurityException.class, () -> r.ventasDelAnio("ana", 2025));
    }
}
