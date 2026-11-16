package taller.templatemethod;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentosTest {

    List<Linea> lineas = List.of(new Linea("Teclado", 2, 100_000), new Linea("Mouse", 1, 50_000));
    Datos venta = new Datos("ACME", lineas, null);
    Datos devolucion = new Datos("ACME", lineas, "FAC-1");

    @Test
    void herencia_esqueletoCompleto() {
        Numerador n = new Numerador();
        Auditoria a = new Auditoria();
        assertEquals("FACTURA FAC-1 ACME total $297500", new Factura(n, a).generar(venta));
        assertEquals("FACTURA FAC-2 ACME total $297500", new Factura(n, a).generar(venta));
        assertEquals("COTIZACIÓN COT-1 ACME total estimado $282625 (válida 30 días)", new Cotizacion(n, a).generar(venta));
        assertEquals("NOTA CRÉDITO NC-1 ACME sobre FAC-1 total $-297500", new NotaCredito(n, a).generar(devolucion));
        assertEquals(List.of("FAC-1 ACME $297500", "FAC-2 ACME $297500", "COT-1 ACME $282625", "NC-1 ACME $-297500"), a.registros());
    }

    @Test
    void composicion_mismoResultado() {
        Numerador n = new Numerador();
        Auditoria a = new Auditoria();
        GeneradorCompuesto g = new GeneradorCompuesto(n, a);
        assertEquals("FACTURA FAC-1 ACME total $297500", g.generar(TiposDocumento.FACTURA, venta));
        assertEquals("COTIZACIÓN COT-1 ACME total estimado $282625 (válida 30 días)", g.generar(TiposDocumento.COTIZACION, venta));
        assertEquals("NOTA CRÉDITO NC-1 ACME sobre FAC-1 total $-297500", g.generar(TiposDocumento.NOTA_CREDITO, devolucion));
        assertEquals(3, a.registros().size());
    }

    @Test
    void notaCreditoSinFacturaFallaEnAmbasVersiones() {
        assertThrows(IllegalArgumentException.class, () -> new NotaCredito(new Numerador(), new Auditoria()).generar(venta));
        assertThrows(IllegalArgumentException.class,
                () -> new GeneradorCompuesto(new Numerador(), new Auditoria()).generar(TiposDocumento.NOTA_CREDITO, venta));
    }

    @Test
    void validacionComunYNadaSeNumeraSiFalla() {
        Numerador n = new Numerador();
        Auditoria a = new Auditoria();
        assertThrows(IllegalArgumentException.class, () -> new Factura(n, a).generar(new Datos("", lineas, null)));
        assertThrows(IllegalArgumentException.class, () -> new Factura(n, a).generar(new Datos("X", List.of(), null)));
        assertTrue(a.registros().isEmpty());
        assertEquals("FAC-1", n.siguiente("FAC"));
    }

    @Test
    void totalesAisladosEnComposicion() {
        // Con composición el cálculo es una función: se prueba sola, sin numerador ni auditoría.
        assertEquals(new Totales(250_000, 47_500, 297_500), TiposDocumento.FACTURA.totales().apply(lineas));
        assertEquals(new Totales(237_500, 45_125, 282_625), TiposDocumento.COTIZACION.totales().apply(lineas));
    }

    @Test
    void totalesAisladosEnHerencia() {
        // Con herencia, calcularTotales es protected y vive en una subclase que exige numerador y auditoría.
        assertEquals(new Totales(250_000, 47_500, 297_500), new Factura(null, null).calcularTotales(lineas));
    }
}
