package tienda.pedidos.infraestructura;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import tienda.pedidos.dominio.ConfirmarPedido;
import tienda.pedidos.dominio.PasarelaPagos;
import tienda.pedidos.dominio.RepositorioPedidos;
import tienda.proveedor.stripe.StripeClient;

/** El ensamblador: conecta implementaciones con interfaces. */
@Configuration
@ComponentScan
public class PedidosConfig {

    @Bean
    StripeClient stripeClient() { return new StripeClient("sk_test_curso"); }

    @Bean
    ConfirmarPedido confirmarPedido(RepositorioPedidos pedidos, PasarelaPagos pagos) {
        return new ConfirmarPedido(pedidos, pagos);
    }
}
