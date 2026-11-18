package pedidos;
public class PagoRechazado extends RuntimeException { public PagoRechazado(String id) { super("pago rechazado para el pedido " + id); } }
