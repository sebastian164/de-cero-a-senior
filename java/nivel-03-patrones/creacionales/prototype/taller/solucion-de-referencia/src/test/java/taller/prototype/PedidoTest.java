package taller.prototype;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PedidoTest {

    private Pedido base() {
        Pedido p = Pedido.nuevo(1, "Ana", new Direccion("Calle 1", "Tunja"), LocalDate.of(2025, 1, 1));
        p.agregarItem(new Item("Café", 2));
        p.pagar();
        return p;
    }

    @Test
    void copiaSuperficialCorrompeElOriginal() {
        Pedido original = base();
        Pedido copia = original.copiaSuperficial();
        copia.direccion().setCiudad("Duitama");
        copia.items().get(0).setCantidad(10);
        copia.agregarItem(new Item("Pan", 1));
        assertEquals("Duitama", original.direccion().ciudad());
        assertEquals(10, original.items().get(0).cantidad());
        assertEquals(2, original.items().size());
    }

    @Test
    void repetirEsCopiaProfundaYNoCopiaIdentidadFechaNiEstado() {
        Pedido original = base();
        Pedido repetido = original.repetir(99, LocalDate.of(2025, 2, 1));
        repetido.direccion().setCiudad("Duitama");
        repetido.items().get(0).setCantidad(10);
        repetido.agregarItem(new Item("Pan", 1));

        assertEquals("Tunja", original.direccion().ciudad());
        assertEquals(2, original.items().get(0).cantidad());
        assertEquals(1, original.items().size());

        assertEquals(99, repetido.numero());
        assertEquals(LocalDate.of(2025, 2, 1), repetido.fecha());
        assertEquals(Estado.CREADO, repetido.estado());
        assertEquals(Estado.PAGADO, original.estado());
    }

    @Test
    void conValoresInmutablesCompartirEsSeguro() {
        PedidoConValores p = new PedidoConValores(1, "Ana", new DireccionInmutable("C", "Tunja"),
                List.of(new ItemInmutable("Café", 2)), LocalDate.of(2025, 1, 1), Estado.ENTREGADO);
        PedidoConValores r = p.repetir(2, LocalDate.of(2025, 2, 1));
        assertSame(p.direccion(), r.direccion());
        assertSame(p.items(), r.items());
        assertThrows(UnsupportedOperationException.class, () -> r.items().add(new ItemInmutable("x", 1)));
        assertEquals("Tunja", p.direccion().ciudad());
        assertEquals("Duitama", r.direccion().conCiudad("Duitama").ciudad());
        assertEquals(Estado.CREADO, r.estado());
    }

    @Test
    void cloneCompartelaLista() {
        assertTrue(ConClone.demostrar());
    }
}
