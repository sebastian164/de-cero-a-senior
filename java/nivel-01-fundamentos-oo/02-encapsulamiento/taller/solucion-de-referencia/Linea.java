/** Línea inmutable: producto, cantidad positiva y precio unitario no negativo. */
public final class Linea {

    private final String producto;
    private final int cantidad;
    private final long precioUnitario;

    public Linea(String producto, int cantidad, long precioUnitario) {
        if (producto == null || producto.isBlank()) {
            throw new IllegalArgumentException("La línea necesita un producto");
        }
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser positiva: " + cantidad);
        }
        if (precioUnitario < 0) {
            throw new IllegalArgumentException("El precio no puede ser negativo");
        }
        this.producto = producto;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
    }

    Linea sumar(int otraCantidad, long otroPrecio) {
        if (otroPrecio != precioUnitario) {
            throw new IllegalArgumentException("El producto " + producto + " ya está con otro precio");
        }
        return new Linea(producto, Math.addExact(cantidad, otraCantidad), precioUnitario);
    }

    public long importe() { return Math.multiplyExact(cantidad, precioUnitario); }
    public String producto() { return producto; }
    public int cantidad() { return cantidad; }
    public long precioUnitario() { return precioUnitario; }
}
