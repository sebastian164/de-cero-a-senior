package tienda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import tienda.dominio.saga.SagaDeCompra;
import tienda.dominio.saga.SagaDeCompra.Estado;
import tienda.dominio.saga.SagaDeCompra.TransicionInvalida;

/** Paso 1: qué transiciones son válidas y cuáles no, sin infraestructura. */
class SagaDeCompraTest {

    @Test
    void caminoFeliz() {
        SagaDeCompra s = SagaDeCompra.iniciar("p1");
        s.inventarioReservado(); s.cobrado(); s.envioProgramado();
        assertEquals(Estado.COMPLETADA, s.estado());
    }

    @Test
    void compensacionTrasEnvioImposible() {
        SagaDeCompra s = SagaDeCompra.iniciar("p1");
        s.inventarioReservado(); s.cobrado(); s.envioImposible(); s.reembolsado(); s.liberado();
        assertEquals(Estado.CANCELADA, s.estado());
        assertEquals("envío imposible", s.motivo());
    }

    @Test
    void noSePuedeSaltarNiRetroceder() {
        assertThrows(TransicionInvalida.class, () -> SagaDeCompra.iniciar("p").cobrado());        // saltar
        SagaDeCompra s = SagaDeCompra.iniciar("p"); s.inventarioReservado();
        assertThrows(TransicionInvalida.class, s::inventarioReservado);                            // repetir
        SagaDeCompra c = SagaDeCompra.iniciar("p"); c.sinExistencias();
        assertThrows(TransicionInvalida.class, c::inventarioReservado);                            // salir de un estado final
        SagaDeCompra d = new SagaDeCompra("p", Estado.COMPLETADA, null);
        assertThrows(TransicionInvalida.class, d::envioImposible);
    }

    @Test
    void tablaCompletaDeTransicionesValidas() {
        // De cada estado, exactamente las operaciones previstas son válidas; el resto lanza.
        int validas = 0;
        for (Estado e : Estado.values()) {
            for (var op : operaciones()) {
                try { op.accept(new SagaDeCompra("p", e, null)); validas++; } catch (TransicionInvalida ignorada) { }
            }
        }
        assertEquals(8, validas);   // las 8 aristas del diagrama, cada una con una sola operación
        assertEquals(EnumSet.of(Estado.COMPLETADA, Estado.CANCELADA), EnumSet.complementOf(abiertas()));
    }

    private static EnumSet<Estado> abiertas() {
        EnumSet<Estado> a = EnumSet.noneOf(Estado.class);
        for (Estado e : Estado.values()) if (e.abierta()) a.add(e);
        return a;
    }

    private static java.util.List<java.util.function.Consumer<SagaDeCompra>> operaciones() {
        return java.util.List.of(SagaDeCompra::inventarioReservado, SagaDeCompra::sinExistencias, SagaDeCompra::cobrado,
                SagaDeCompra::pagoRechazado, SagaDeCompra::envioProgramado, SagaDeCompra::envioImposible,
                SagaDeCompra::reembolsado, SagaDeCompra::liberado);
    }
}
