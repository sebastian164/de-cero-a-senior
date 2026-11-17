package tienda.integrador.inventario.dominio;

/** La regla de negocio de Inventario: reservar descuenta, liberar devuelve, y liberar sin reserva deja una lápida. Sin tecnología. */
public final class Reserva {

    private Reserva() {
    }

    public static boolean cantidadValida(int cantidad) {
        return cantidad > 0;
    }
}
