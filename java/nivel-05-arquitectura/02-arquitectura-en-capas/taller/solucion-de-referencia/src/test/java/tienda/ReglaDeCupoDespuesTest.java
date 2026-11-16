package tienda;

import org.junit.jupiter.api.Test;
import tienda.despues.clientes.Cliente;
import tienda.despues.pedidos.CupoExcedido;
import tienda.despues.pedidos.EstadoPedido;
import tienda.despues.pedidos.Pedido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Después: la regla se prueba con objetos, sin simular nada. */
class ReglaDeCupoDespuesTest {

    private final Cliente ana = new Cliente(1L, "Ana", 1_000_000);

    @Test
    void confirmaSiCabeEnElCupo() {
        Pedido pedido = new Pedido(11L, ana, 300_000);
        pedido.confirmar(700_000);
        assertThat(pedido.estado()).isEqualTo(EstadoPedido.CONFIRMADO);
    }

    @Test
    void rechazaSiSuperaElCupo() {
        assertThatThrownBy(() -> new Pedido(12L, ana, 300_001).confirmar(700_000)).isInstanceOf(CupoExcedido.class);
    }

    @Test
    void soloSeConfirmaUnBorrador() {
        Pedido pedido = new Pedido(11L, ana, 1);
        pedido.confirmar(0);
        assertThatThrownBy(() -> pedido.confirmar(0)).isInstanceOf(IllegalStateException.class);
    }
}
