package tienda;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tienda.pedidos.dominio.ConfirmarPedido;
import tienda.pedidos.dominio.Dinero;
import tienda.pedidos.dominio.MedioDePago;
import tienda.pedidos.dominio.PasarelaPagos;
import tienda.pedidos.dominio.Pedido;
import tienda.pedidos.dominio.ResultadoPago;
import tienda.pedidos.infraestructura.PedidosConfig;
import tienda.pedidos.infraestructura.RepositorioPedidosEnMemoria;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfirmarPedidoTest {

    /** Implementación en memoria del puerto de pagos: sin Stripe, sin red. */
    static class PasarelaFalsa implements PasarelaPagos {
        final List<Dinero> cobros = new ArrayList<>();
        boolean aprobar = true;

        @Override public ResultadoPago cobrar(Dinero monto, MedioDePago medio) {
            cobros.add(monto);
            return aprobar ? new ResultadoPago.Aprobado("AUT-1") : new ResultadoPago.Rechazado("fondos");
        }
    }

    @Test
    void confirmaConUnaPasarelaEnMemoria() {
        var repo = new RepositorioPedidosEnMemoria();
        repo.guardar(new Pedido(1, new Dinero(50_000)));
        var pagos = new PasarelaFalsa();

        Pedido pedido = new ConfirmarPedido(repo, pagos).ejecutar(1, new MedioDePago("cualquiera"));

        assertThat(pedido.estado()).isEqualTo(Pedido.Estado.CONFIRMADO);
        assertThat(pedido.autorizacion()).isEqualTo("AUT-1");
        assertThat(pagos.cobros).containsExactly(new Dinero(50_000));
    }

    @Test
    void registraElRechazo() {
        var repo = new RepositorioPedidosEnMemoria();
        repo.guardar(new Pedido(2, new Dinero(10)));
        var pagos = new PasarelaFalsa();
        pagos.aprobar = false;

        assertThat(new ConfirmarPedido(repo, pagos).ejecutar(2, new MedioDePago("x")).estado())
                .isEqualTo(Pedido.Estado.PAGO_RECHAZADO);
    }

    @Test
    void springEnsamblaElAdaptadorReal() {
        try (var contexto = new AnnotationConfigApplicationContext(PedidosConfig.class)) {
            contexto.getBean(RepositorioPedidosEnMemoria.class).guardar(new Pedido(3, new Dinero(20_000)));
            Pedido pedido = contexto.getBean(ConfirmarPedido.class).ejecutar(3, new MedioDePago("tok_ok_visa"));
            assertThat(pedido.estado()).isEqualTo(Pedido.Estado.CONFIRMADO);
            assertThat(pedido.autorizacion()).startsWith("pi_");
        }
    }
}
