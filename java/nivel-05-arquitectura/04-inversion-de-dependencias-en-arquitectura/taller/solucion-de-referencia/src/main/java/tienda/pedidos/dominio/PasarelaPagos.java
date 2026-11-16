package tienda.pedidos.dominio;

/** Contrato definido por el negocio, con su vocabulario: cobrar, no createPaymentIntent. */
public interface PasarelaPagos {
    ResultadoPago cobrar(Dinero monto, MedioDePago medio);
}
