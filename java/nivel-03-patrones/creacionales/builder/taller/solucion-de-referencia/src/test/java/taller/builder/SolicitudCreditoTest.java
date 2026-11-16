package taller.builder;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SolicitudCreditoTest {

    @Test
    void valoresPorDefecto() {
        SolicitudCredito s = SolicitudCredito.builder("Ana", 1_000_000).build();
        assertEquals(12, s.plazoMeses());
        assertEquals(new BigDecimal("1.5"), s.tasaMensual());
        assertTrue(s.codeudor().isEmpty());
        assertTrue(s.seguro().isEmpty());
        assertTrue(s.cuentaDesembolso().isEmpty());
    }

    @Test
    void tasaPorDefectoDependeDelPlazo() {
        assertEquals(new BigDecimal("1.8"), SolicitudCredito.builder("Ana", 1).plazoMeses(36).build().tasaMensual());
        assertEquals(new BigDecimal("2.1"),
                SolicitudCredito.builder("Ana", 1).plazoMeses(72).seguro("S").build().tasaMensual());
        assertEquals(new BigDecimal("1.2"),
                SolicitudCredito.builder("Ana", 1).tasaMensual(new BigDecimal("1.2")).build().tasaMensual());
    }

    @Test
    void montoDebeSerPositivo() {
        var e = assertThrows(IllegalStateException.class, () -> SolicitudCredito.builder("Ana", 0).build());
        assertEquals("El monto debe ser mayor que cero", e.getMessage());
    }

    @Test
    void plazoEntre6y84() {
        assertThrows(IllegalStateException.class, () -> SolicitudCredito.builder("Ana", 1).plazoMeses(5).build());
        assertThrows(IllegalStateException.class, () -> SolicitudCredito.builder("Ana", 1).plazoMeses(85).seguro("S").build());
        assertDoesNotThrow(() -> SolicitudCredito.builder("Ana", 1).plazoMeses(6).build());
        assertDoesNotThrow(() -> SolicitudCredito.builder("Ana", 1).plazoMeses(84).seguro("S").build());
    }

    @Test
    void codeudorObligatorioSobre50Millones() {
        assertDoesNotThrow(() -> SolicitudCredito.builder("Ana", 50_000_000).build());
        var e = assertThrows(IllegalStateException.class, () -> SolicitudCredito.builder("Ana", 50_000_001).build());
        assertTrue(e.getMessage().contains("codeudor"));
        assertDoesNotThrow(() -> SolicitudCredito.builder("Ana", 50_000_001).codeudor("Luis").build());
    }

    @Test
    void seguroObligatorioSobre60Meses() {
        assertDoesNotThrow(() -> SolicitudCredito.builder("Ana", 1).plazoMeses(60).build());
        var e = assertThrows(IllegalStateException.class, () -> SolicitudCredito.builder("Ana", 1).plazoMeses(61).build());
        assertTrue(e.getMessage().contains("seguro"));
    }

    @Test
    void elOrdenDeLasLlamadasNoImporta() {
        // Si la regla se validara en codeudor(...) o en plazoMeses(...), este orden fallaría.
        assertDoesNotThrow(() -> SolicitudCredito.builder("Ana", 80_000_000)
                .plazoMeses(72).seguro("S").codeudor("Luis").build());
    }

    @Test
    void esInmutable() {
        for (var campo : SolicitudCredito.class.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(campo.getModifiers())) {
                assertTrue(java.lang.reflect.Modifier.isFinal(campo.getModifiers()), campo.getName());
            }
        }
    }
}
