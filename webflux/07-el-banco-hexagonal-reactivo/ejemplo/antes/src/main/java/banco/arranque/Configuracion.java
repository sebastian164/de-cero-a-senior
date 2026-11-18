package banco.arranque;

import banco.adaptadores.salida.jdbc.AlmacenJdbc;
import banco.adaptadores.salida.jdbc.UnidadDeTrabajoJdbc;
import banco.aplicacion.ServicioDeCuentas;
import banco.aplicacion.puertos.salida.Reloj;
import java.time.Instant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** La raíz de composición. El servicio es el del módulo hexagonal, sin una línea cambiada. */
@Configuration
public class Configuracion {

    @Bean
    public Reloj reloj() {
        return Instant::now;
    }

    @Bean
    public ServicioDeCuentas servicioDeCuentas(AlmacenJdbc almacen, UnidadDeTrabajoJdbc unidadDeTrabajo, Reloj reloj) {
        return new ServicioDeCuentas(almacen, almacen, almacen, unidadDeTrabajo, reloj, almacen);
    }
}
