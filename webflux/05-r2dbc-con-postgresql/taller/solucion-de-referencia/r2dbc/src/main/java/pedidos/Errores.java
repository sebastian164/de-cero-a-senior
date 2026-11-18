package pedidos;

public final class Errores {
    private Errores() {}
    public static class PedidoNoEncontrado extends RuntimeException { public PedidoNoEncontrado(String id) { super("no existe el pedido " + id); } }
    public static class SinExistencias extends RuntimeException { public SinExistencias(String producto) { super("sin existencias de " + producto); } }
}
