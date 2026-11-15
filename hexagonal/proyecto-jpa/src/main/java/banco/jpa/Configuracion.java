package banco.jpa;

import banco.aplicacion.ServicioDeCuentas;
import banco.aplicacion.puertos.salida.Reloj;
import banco.aplicacion.puertos.salida.UnidadDeTrabajo;
import java.time.Instant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** La raíz de composición. El núcleo no lleva ninguna anotación de Spring: se arma aquí. */
@Configuration
public class Configuracion {

    @Bean
    public Reloj reloj() {
        return Instant::now;
    }

    @Bean
    public ServicioDeCuentas servicioDeCuentas(AlmacenJpa almacen, UnidadDeTrabajo unidadDeTrabajo, Reloj reloj) {
        return new ServicioDeCuentas(almacen, almacen, almacen, unidadDeTrabajo, reloj, almacen);
    }
}
