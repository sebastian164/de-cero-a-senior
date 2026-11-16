package tienda.pedidos;

import org.junit.jupiter.api.Test;
import tienda.pedidos.dominio.CancelacionRechazada;
import tienda.pedidos.dominio.EstadoPedido;
import tienda.pedidos.dominio.Pedido;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sin Spring: solo el modelo. */
class ReglasDeCancelacionTest {

    private static final LocalDateTime CREADO = LocalDateTime.of(2026, 1, 10, 9, 0);

    @Test
    void cancelaDentroDelPlazo() {
        Pedido p = new Pedido(1, "ana@x.co", CREADO, EstadoPedido.CREADO);
        p.cancelar(CREADO.plusHours(24));                       // justo en el límite: se permite
        assertThat(p.estado()).isEqualTo(EstadoPedido.CANCELADO);
    }

    @Test
    void noCancelaUnPedidoEnviado() {
        Pedido p = new Pedido(1, "ana@x.co", CREADO, EstadoPedido.ENVIADO);
        assertThatThrownBy(() -> p.cancelar(CREADO.plusHours(1)))
                .isInstanceOf(CancelacionRechazada.class).hasMessage("Ya fue enviado");
    }

    @Test
    void noCancelaPasadas24Horas() {
        Pedido p = new Pedido(1, "ana@x.co", CREADO, EstadoPedido.CREADO);
        assertThatThrownBy(() -> p.cancelar(CREADO.plusHours(24).plusSeconds(1)))
                .isInstanceOf(CancelacionRechazada.class).hasMessage("Pasaron más de 24 horas");
    }
}
