package banco.arranque;

import banco.adaptadores.salida.r2dbc.AlmacenR2dbc;
import banco.adaptadores.salida.r2dbc.UnidadDeTrabajoR2dbc;
import banco.aplicacion.puertos.salida.Reloj;
import banco.aplicacion.reactiva.ServicioDeCuentas;
import java.time.Instant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** La raíz de composición. Mismo esquema que antes: el servicio no lleva anotaciones de Spring y se arma aquí. */
@Configuration
public class Configuracion {

    @Bean
    public Reloj reloj() {
        return Instant::now;
    }

    @Bean
    public ServicioDeCuentas servicioDeCuentas(AlmacenR2dbc almacen, UnidadDeTrabajoR2dbc unidadDeTrabajo, Reloj reloj) {
        return new ServicioDeCuentas(almacen, almacen, almacen, unidadDeTrabajo, reloj, almacen);
    }
}
