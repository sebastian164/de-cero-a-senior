package tienda.antes.service;

import org.springframework.stereotype.Service;
import tienda.proveedor.stripe.StripeClient;

import java.util.HashMap;
import java.util.Map;

/** Antes: el servicio de negocio importa el SDK del proveedor y Spring. */
@Service
public class PedidoService {
    private final StripeClient stripe;
    private final Map<Long, Long> totales = new HashMap<>();

    public PedidoService(StripeClient stripe) { this.stripe = stripe; }

    public String confirmar(long pedidoId, String token) {
        long totalPesos = totales.getOrDefault(pedidoId, 0L);
        StripeClient.PaymentIntent intent = stripe.createPaymentIntent(totalPesos * 100, "cop", token);
        if (!"succeeded".equals(intent.status())) throw new IllegalStateException("pago rechazado");
        return "CONFIRMADO";
    }
}
