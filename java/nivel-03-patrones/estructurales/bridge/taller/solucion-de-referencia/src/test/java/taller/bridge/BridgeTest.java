package taller.bridge;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BridgeTest {

    @Test
    void laVersionPorCombinacionTieneNueveClases() {
        assertEquals(9, combinaciones.Combinaciones.CLASES.size());
    }

    @Test
    void cualquierReporteConCualquierFormato() {
        assertEquals("[PDF] Ventas\n[PDF] Teclado | 12\n[PDF] Mouse | 30", new ReporteVentas(new Pdf()).generar());
        assertEquals("== Inventario ==\nTeclado  4 en bodega", new ReporteInventario(new Pantalla()).generar());
        assertEquals("# Nómina\nAna,4500000", new ReporteNomina(new Csv()).generar());
        assertEquals("[XLSX] hoja 'Nómina'\n[XLSX] Ana\t4500000", new ReporteNomina(new Excel()).generar());
    }

    @Test
    void carteraUsaColorSoloDondeElFormatoLoSoporta() {
        assertTrue(new ReporteCartera(new Pdf()).generar().contains("{color:rojo}vencida 90 días{/color}"));
        assertTrue(new ReporteCartera(new Excel()).generar().contains("<fill FF0000>vencida 90 días</fill>"));
        assertTrue(new ReporteCartera(new Pantalla()).generar().contains("!! vencida 90 días"));
        assertTrue(new ReporteCartera(new Csv()).generar().contains("!! vencida 90 días"));
    }

    /** Un formato que la abstracción no conoce: si ReporteCartera preguntara por el tipo, esto no funcionaría. */
    @Test
    void unFormatoNuevoSinColorDegradaSinTocarLaAbstraccion() {
        Formato json = new Formato() {
            public String titulo(String t) { return "{\"titulo\":\"" + t + "\"}"; }
            public String fila(java.util.List<String> c) { return String.join("|", c); }
        };
        assertEquals("{\"titulo\":\"Cartera\"}\nAna|!! vencida 90 días\nLuis|al día", new ReporteCartera(json).generar());
        Formato conColor = new Formato() {
            public String titulo(String t) { return t; }
            public String fila(java.util.List<String> c) { return String.join("|", c); }
            @Override public String resaltar(String t, Nivel n) { return n == Nivel.ALERTA ? "<b>" + t + "</b>" : t; }
        };
        assertTrue(new ReporteCartera(conColor).generar().contains("Ana|<b>vencida 90 días</b>"));
    }
}
