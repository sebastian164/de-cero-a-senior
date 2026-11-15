public class Item {

    private final String descripcion;
    private final int cantidad;
    private final long precioUnitario; // en pesos

    public Item(String descripcion, int cantidad, long precioUnitario) {
        if (descripcion == null || descripcion.isBlank()) {
            throw new IllegalArgumentException("El ítem necesita una descripción");
        }
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        }
        if (precioUnitario < 0) {
            throw new IllegalArgumentException("El precio no puede ser negativo");
        }
        this.descripcion = descripcion;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public int getCantidad() {
        return cantidad;
    }

    public long getPrecioUnitario() {
        return precioUnitario;
    }

    public long importe() {
        return cantidad * precioUnitario;
    }
}
