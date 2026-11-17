package tienda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tienda.SagaDeCompra.Compra;
import tienda.SagaDeCompra.Resultado;

class SagaDeCompraTest {

    private SagaDeCompra saga() {
        SagaDeCompra saga = Main.nueva();
        return saga;
    }

    /** Consistente: confirmado con cobro, reserva y despacho; o cancelado sin cobro vigente, sin reserva y sin despacho. */
    private void consistente(SagaDeCompra s, String pedido) {
        String estado = s.pedidos.estados.get(pedido);
        if (estado.equals("CONFIRMADO")) {
            assertTrue(s.pagos.cobros.containsKey(pedido));
            assertTrue(s.inventario.reservas.containsKey(pedido));
            assertTrue(s.envios.despachos.contains(pedido));
        } else {
            assertTrue(estado.startsWith("CANCELADO"), estado);
            assertTrue(!s.pagos.cobros.containsKey(pedido));
            assertTrue(!s.inventario.reservas.containsKey(pedido));
            assertTrue(!s.envios.despachos.contains(pedido));
            assertEquals(10, s.inventario.existencias.get("camisa"), "las existencias vuelven a su valor");
        }
    }

    @Test
    void compraExitosa() {
        SagaDeCompra s = saga();
        assertEquals(Resultado.CONFIRMADA, s.ejecutar(new Compra("p-1", "ana", "camisa", 2, 200_000, "Bogotá")));
        consistente(s, "p-1");
        assertEquals(8, s.inventario.existencias.get("camisa"));
    }

    @Test
    void pagoRechazadoLiberaElInventario() {
        SagaDeCompra s = saga();
        assertEquals(Resultado.CANCELADA, s.ejecutar(new Compra("p-2", "ana", "camisa", 2, 5_000_000, "Bogotá")));
        consistente(s, "p-2");
        assertTrue(s.traza.contains("p-2: compensado reservar inventario"));
        assertTrue(s.pagos.reembolsos.isEmpty(), "no hubo cobro, no hay reembolso");
    }

    @Test
    void sinExistenciasNoCompensaNada() {
        SagaDeCompra s = saga();
        assertEquals(Resultado.CANCELADA, s.ejecutar(new Compra("p-3", "ana", "camisa", 50, 200_000, "Bogotá")));
        consistente(s, "p-3");
        assertTrue(s.traza.stream().noneMatch(l -> l.contains("compensado")));
    }

    @Test
    void falloEnElEnvioReembolsaYLiberaEnOrdenInverso() {
        SagaDeCompra s = saga();
        assertEquals(Resultado.CANCELADA, s.ejecutar(new Compra("p-4", "ana", "camisa", 2, 200_000, "Leticia")));
        consistente(s, "p-4");
        List<String> compensaciones = s.traza.stream().filter(l -> l.contains("compensado")).toList();
        assertEquals(List.of("p-4: compensado cobrar", "p-4: compensado reservar inventario"), compensaciones);
        assertEquals(List.of("p-4:200000"), s.pagos.reembolsos, "el reembolso deja rastro");
    }

    @Test
    void quintoPasoSumaPuntosSoloSiSeConfirma() {
        SagaDeCompra s = saga();
        s.conPuntosDeFidelidad = true;
        s.ejecutar(new Compra("p-5", "ana", "camisa", 2, 200_000, "Bogotá"));
        s.ejecutar(new Compra("p-6", "ana", "camisa", 2, 200_000, "Leticia"));
        assertEquals(Map.of("ana", 2L), s.fidelidad.puntos);
    }

    @Test
    void compensacionQueFallaDejaElSistemaInconsistente() {
        SagaDeCompra s = saga();
        s.inventario.compensacionRota = true;
        assertEquals(Resultado.INCONSISTENTE, s.ejecutar(new Compra("p-7", "ana", "camisa", 2, 5_000_000, "Bogotá")));
        // El paso 8 pide describirlo: la reserva queda colgada y nadie lo reintentará.
        assertEquals(Map.of("p-7", 2), s.inventario.reservas);
        assertEquals(8, s.inventario.existencias.get("camisa"));
        assertEquals("INCONSISTENTE", s.pedidos.estados.get("p-7"));
    }
}
