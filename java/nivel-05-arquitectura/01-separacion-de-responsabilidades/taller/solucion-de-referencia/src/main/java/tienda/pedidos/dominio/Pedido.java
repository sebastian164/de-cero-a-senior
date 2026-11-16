package tienda.pedidos.dominio;

import java.util.Objects;

/** Dominio: aquí vive la regla del descuento por volumen. No importa nada técnico. */
public final class Pedido {

    public static final long PRECIO_UNITARIO = 40_000;
    public static final long UMBRAL_DESCUENTO = 1_000_000;

    private final Long id;
    private final String cliente;
    private final int cantidad;
    private final long total;

    private Pedido(Long id, String cliente, int cantidad, long total) {
        this.id = id;
        this.cliente = cliente;
        this.cantidad = cantidad;
        this.total = total;
    }

    public static Pedido nuevo(String cliente, int cantidad) {
        if (cliente == null || cliente.isBlank()) throw new IllegalArgumentException("cliente obligatorio");
        if (cantidad <= 0) throw new IllegalArgumentException("cantidad debe ser positiva");
        Pedido sinTotal = new Pedido(null, cliente, cantidad, 0);
        return new Pedido(null, cliente, cantidad, sinTotal.calcularTotalConDescuentoPorVolumen());
    }

    public static Pedido reconstituir(Long id, String cliente, int cantidad, long total) {
        return new Pedido(id, cliente, cantidad, total);
    }

    public Pedido conId(long id) {
        return new Pedido(id, cliente, cantidad, total);
    }

    private long calcularTotalConDescuentoPorVolumen() {
        long bruto = cantidad * PRECIO_UNITARIO;
        return bruto > UMBRAL_DESCUENTO ? bruto * 90 / 100 : bruto;
    }

    public Long id() { return id; }
    public String cliente() { return cliente; }
    public int cantidad() { return cantidad; }
    public long total() { return total; }

    @Override public boolean equals(Object o) {
        return o instanceof Pedido p && Objects.equals(id, p.id) && cliente.equals(p.cliente)
                && cantidad == p.cantidad && total == p.total;
    }
    @Override public int hashCode() { return Objects.hash(id, cliente, cantidad, total); }
}
