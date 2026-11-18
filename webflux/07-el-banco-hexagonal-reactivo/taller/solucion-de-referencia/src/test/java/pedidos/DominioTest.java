package pedidos;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tienda.dominio.*;

/** El dominio de la tienda, compilado desde hexagonal/08.../solucion-de-referencia/src, se comporta igual sin importar quién lo llame. */
class DominioTest {
    @Test void confirmarCambiaElEstado() {
        Pedido p = Datos.pedido("p", EstadoDePedido.PENDIENTE, "a");
        p.confirmar();
        assertEquals(EstadoDePedido.CONFIRMADO, p.estado());
    }
    @Test void noSeConfirmaDosVeces() {
        Pedido p = Datos.pedido("p", EstadoDePedido.CONFIRMADO, "a");
        assertThrows(ReglaDeNegocioViolada.class, p::confirmar);
    }
    @Test void laReservaEsTodoONada() {
        Pedido p = Datos.pedido("p", EstadoDePedido.PENDIENTE, "a", "b");
        Existencias a = Existencias.de("a", 5), b = Existencias.de("b", 1);
        assertThrows(ReglaDeNegocioViolada.class, () -> Reserva.reservar(p, Map.of("a", a, "b", b)));
        assertEquals(5, a.unidades(), "no se tocó nada de a");
    }
}
