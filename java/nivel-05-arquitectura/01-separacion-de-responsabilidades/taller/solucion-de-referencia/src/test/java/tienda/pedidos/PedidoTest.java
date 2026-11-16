package tienda.pedidos;

import org.junit.jupiter.api.Test;
import tienda.pedidos.dominio.Pedido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La regla se prueba sin HTTP, sin base de datos y sin Spring. */
class PedidoTest {

    @Test
    void sinDescuentoHastaElUmbral() {
        assertThat(Pedido.nuevo("ana", 10).total()).isEqualTo(400_000);
        assertThat(Pedido.nuevo("ana", 25).total()).isEqualTo(1_000_000);   // justo en el umbral: sin descuento
    }

    @Test
    void diezPorCientoSobreElUmbral() {
        assertThat(Pedido.nuevo("ana", 30).total()).isEqualTo(1_080_000);   // 1.200.000 - 10 %
    }

    @Test
    void rechazaDatosInvalidos() {
        assertThatThrownBy(() -> Pedido.nuevo("", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Pedido.nuevo("ana", 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
