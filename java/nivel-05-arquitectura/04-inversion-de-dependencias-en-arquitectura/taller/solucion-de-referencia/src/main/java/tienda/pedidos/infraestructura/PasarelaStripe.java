package tienda.pedidos.infraestructura;

import org.springframework.stereotype.Component;
import tienda.pedidos.dominio.Dinero;
import tienda.pedidos.dominio.MedioDePago;
import tienda.pedidos.dominio.PasarelaPagos;
import tienda.pedidos.dominio.ResultadoPago;
import tienda.proveedor.stripe.StripeClient;

/** La infraestructura implementa el contrato del negocio y traduce al vocabulario del proveedor. */
@Component
class PasarelaStripe implements PasarelaPagos {
    private final StripeClient stripe;

    PasarelaStripe(StripeClient stripe) { this.stripe = stripe; }

    @Override
    public ResultadoPago cobrar(Dinero monto, MedioDePago medio) {
        StripeClient.PaymentIntent intent = stripe.createPaymentIntent(monto.pesos() * 100, "cop", medio.referencia());
        return "succeeded".equals(intent.status())
                ? new ResultadoPago.Aprobado(intent.id())
                : new ResultadoPago.Rechazado(intent.status());
    }
}
