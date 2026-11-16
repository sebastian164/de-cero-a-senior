package tienda.jpa;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tienda.aplicacion.ServicioDePedidos;
import tienda.aplicacion.puertos.salida.Reloj;
import tienda.aplicacion.puertos.salida.UnidadDeTrabajo;

/** La raíz de composición. El núcleo no lleva ninguna anotación de Spring. */
@Configuration
public class Configuracion {

    @Bean
    public Reloj reloj() {
        return () -> Instant.now().truncatedTo(ChronoUnit.MICROS);    // PostgreSQL guarda microsegundos
    }

    @Bean
    public ServicioDePedidos servicioDePedidos(AlmacenJpa almacen, CatalogoJpa catalogo, UnidadDeTrabajo unidadDeTrabajo, Reloj reloj) {
        return new ServicioDePedidos(almacen, catalogo, almacen, almacen, unidadDeTrabajo, reloj, almacen);
    }
}
