package tienda.proveedor.stripe;

/** Simula el SDK de un proveedor: su vocabulario, no el nuestro. */
public class StripeClient {
    public record PaymentIntent(String id, String status) { }

    private final String apiKey;

    public StripeClient(String apiKey) { this.apiKey = apiKey; }

    public PaymentIntent createPaymentIntent(long amountInCents, String currency, String paymentMethodToken) {
        if (apiKey.isBlank()) throw new IllegalStateException("missing api key");
        String status = paymentMethodToken.startsWith("tok_ok") ? "succeeded" : "requires_payment_method";
        return new PaymentIntent("pi_" + Math.abs(paymentMethodToken.hashCode()), status);
    }
}
