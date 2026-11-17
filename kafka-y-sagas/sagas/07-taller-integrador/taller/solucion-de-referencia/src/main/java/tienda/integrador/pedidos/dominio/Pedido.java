package tienda.integrador.pedidos.dominio;

/** Un pedido. Nace PENDIENTE y solo el resultado de la saga lo lleva a CONFIRMADO o CANCELADO. Sin ninguna tecnología. */
public record Pedido(String id, String correlacion, String producto, int cantidad, long centavos, String ciudad, Estado estado, String motivo) {

    public enum Estado { PENDIENTE, CONFIRMADO, CANCELADO }

    public Pedido {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("falta el id del pedido");
        if (cantidad <= 0) throw new IllegalArgumentException("la cantidad debe ser positiva");
        if (centavos <= 0) throw new IllegalArgumentException("el importe debe ser positivo");
    }

    public static Pedido nuevo(String id, String correlacion, String producto, int cantidad, long centavos, String ciudad) {
        return new Pedido(id, correlacion, producto, cantidad, centavos, ciudad, Estado.PENDIENTE, null);
    }
}
