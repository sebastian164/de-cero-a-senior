package tienda.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tienda.aplicacion.puertos.salida.Catalogo;
import tienda.dominio.Dinero;

/** Precios de la tabla producto. Un adaptador aparte: mañana puede ser un cliente HTTP de otro servicio. */
@Component
public class CatalogoJpa implements Catalogo {

    @PersistenceContext
    private EntityManager entidades;

    @Override
    public Optional<Dinero> precioDe(String producto) {
        return Optional.ofNullable(entidades.find(ProductoJpa.class, producto)).map(fila -> new Dinero(fila.precioCentavos()));
    }
}
