package reservas.infraestructura;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reservas.aplicacion.Puertos.*;
import reservas.aplicacion.ServicioReservas;

import java.time.Clock;

@Configuration
public class Configuracion {
    @Bean Clock reloj() { return Clock.systemUTC(); }

    @Bean Transaccion transaccion(PlatformTransactionManager tm) {
        TransactionTemplate t = new TransactionTemplate(tm);
        return new Transaccion() {
            public <T> T ejecutar(java.util.function.Supplier<T> trabajo) { return t.execute(s -> trabajo.get()); }
        };
    }

    @Bean ServicioReservas servicioReservas(RepositorioReservas repo, Transaccion tx, Clock reloj) {
        return new ServicioReservas(repo, tx, reloj);
    }
}
