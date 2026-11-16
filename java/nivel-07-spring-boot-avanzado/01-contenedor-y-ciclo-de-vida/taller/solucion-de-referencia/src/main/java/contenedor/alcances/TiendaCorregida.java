package contenedor.alcances;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class TiendaCorregida {
    private final ObjectProvider<Carrito> carritos;
    public TiendaCorregida(ObjectProvider<Carrito> carritos) { this.carritos = carritos; }
    public Carrito carritoNuevo() { return carritos.getObject(); }      // una instancia nueva cada vez
}
