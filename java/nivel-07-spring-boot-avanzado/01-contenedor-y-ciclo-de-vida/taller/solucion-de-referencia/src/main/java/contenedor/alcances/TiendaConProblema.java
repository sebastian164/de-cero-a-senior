package contenedor.alcances;

import org.springframework.stereotype.Component;

/** Singleton que recibe el prototype una sola vez: todos los clientes comparten el mismo carrito. */
@Component
public class TiendaConProblema {
    private final Carrito carrito;
    public TiendaConProblema(Carrito carrito) { this.carrito = carrito; }
    public Carrito carritoNuevo() { return carrito; }
}
