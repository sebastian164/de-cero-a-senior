package inventario.dominio;

/** La regla de reserva: no se puede reservar más de lo que hay. */
public record Existencias(String producto, int disponibles) {
    public boolean puedeReservar(int cantidad) {
        return cantidad > 0 && disponibles >= cantidad;
    }

    public Existencias reservar(int cantidad) {
        if (!puedeReservar(cantidad)) throw new IllegalStateException("no hay " + cantidad + " de " + producto);
        return new Existencias(producto, disponibles - cantidad);
    }
}
