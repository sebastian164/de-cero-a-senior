package pedidos;
public class PagoDemasiadoLento extends RuntimeException {
    public PagoDemasiadoLento(String id) { super("el cobro del pedido " + id + " superó el tiempo límite"); }
}
