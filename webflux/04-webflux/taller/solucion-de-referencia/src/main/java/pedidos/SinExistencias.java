package pedidos;
public class SinExistencias extends RuntimeException { public SinExistencias(String id) { super("sin existencias para el pedido " + id); } }
