package tienda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tienda.bus.Bus;

class CoreografiaTest {

    /** Pasos 2 y 5: los caminos terminan consistentes con los dos órdenes (reservar primero o cobrar primero). */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void losCaminosConLosDosOrdenes(boolean cobrarPrimero) {
        Tienda t = new Tienda(3, cobrarPrimero, false);
        t.envios.sinCobertura.add("envio");
        t.comprar("exito", 200_000);
        t.comprar("rechazado", 5_000_000);
        t.comprar("envio", 100_000);
        t.correr(1);
        assertEquals("CONFIRMADO", t.pedidos.estados.get("exito"));
        assertEquals("CANCELADO", t.pedidos.estados.get("rechazado"));
        assertEquals("CANCELADO", t.pedidos.estados.get("envio"));
        assertEquals("", t.inconsistencias(), t.resumen());
        assertEquals(List.of("envio"), t.pagos.reembolsos);
        assertEquals(2, t.inventario.existencias);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sinExistencias(boolean cobrarPrimero) {
        Tienda t = new Tienda(0, cobrarPrimero, false);
        t.comprar("agotado", 100_000);
        t.correr(1);
        assertEquals("CANCELADO", t.pedidos.estados.get("agotado"));
        assertEquals("", t.inconsistencias(), t.resumen());
        // Con el cobro primero, el cliente ve un cobro y un reembolso; con la reserva primero, nada
        assertEquals(cobrarPrimero ? List.of("agotado") : List.of(), t.pagos.reembolsos);
    }

    @Test
    void fidelidadSoloEscucha() {
        Tienda t = new Tienda(5, false, true);
        t.comprar("bien", 100_000);
        t.comprar("mal", 5_000_000);
        t.correr(1);
        assertEquals(List.of("bien"), List.copyOf(t.fidelidad.puntos));
    }

    /** Paso 6: un servicio caído no produce ningún error. Solo el vigilante de Pedidos lo ve. */
    @Test
    void unServicioCaidoSoloSeDetectaPorTiempo() {
        Tienda t = new Tienda(5, false, false);
        t.envios.caido = true;
        t.comprar("c", 100_000);
        t.correr(30);
        assertEquals("PENDIENTE", t.pedidos.estados.get("c"));
        assertTrue(t.bus.descartados.isEmpty(), "ningún error en ninguna parte");
        assertEquals(List.of("c"), t.pedidos.atascados(t.ciclo, 20));
    }

    /** Paso 7: un ciclo accidental. Con la profundidad máxima se corta y el evento se aparta. */
    @Test
    void unCicloAccidentalSeCorta() {
        Tienda t = new Tienda(5, false, false);
        t.bus.suscribir("PedidoConfirmado", e -> t.bus.publicar("InventarioAjustado", e));
        t.bus.suscribir("InventarioAjustado", e -> t.bus.publicar("PedidoConfirmado", e));
        t.comprar("c", 100_000);
        int entregados = t.bus.entregar();
        assertTrue(entregados <= Bus.PROFUNDIDAD_MAXIMA + 1, "terminó tras " + entregados + " eventos");
        assertEquals(1, t.bus.descartados.size());
        assertEquals(Bus.PROFUNDIDAD_MAXIMA + 1, t.bus.descartados.get(0).profundidad());
    }

    /** Paso 8: reconstruir el estado desde la traza. */
    @Test
    void reconstruccionDesdeLaTraza() {
        Tienda t = new Tienda(5, false, false);
        t.envios.sinCobertura.add("envio");
        t.comprar("bien", 100_000);
        t.comprar("envio", 100_000);
        t.correr(1);
        assertEquals("CONFIRMADA", Reconstruccion.estado(t.bus.traza, "bien"));
        assertEquals("CANCELADA", Reconstruccion.estado(t.bus.traza, "envio"));
        Tienda atascada = new Tienda(5, false, false);
        atascada.envios.caido = true;
        atascada.comprar("c", 100_000);
        atascada.correr(1);
        assertEquals("EN CURSO (reservado, cobrado)", Reconstruccion.estado(atascada.bus.traza, "c"));
    }
}
