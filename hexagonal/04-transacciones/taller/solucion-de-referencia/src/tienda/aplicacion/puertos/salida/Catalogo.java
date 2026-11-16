package tienda.aplicacion.puertos.salida;

import java.util.Optional;
import tienda.dominio.Dinero;

/** Cuánto cuesta hoy un producto. Solo se lee: puede venir de otro servicio. */
public interface Catalogo {

    Optional<Dinero> precioDe(String producto);
}
