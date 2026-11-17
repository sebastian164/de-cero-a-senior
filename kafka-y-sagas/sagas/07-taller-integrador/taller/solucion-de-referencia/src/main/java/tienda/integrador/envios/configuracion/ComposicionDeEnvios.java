package tienda.integrador.envios.configuracion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tienda.integrador.envios.adaptadores.entrada.kafka.EscuchaDeOrdenes;
import tienda.integrador.envios.adaptadores.salida.jdbc.EnviosJdbc;
import tienda.integrador.envios.aplicacion.ServicioDeEnvios;

/** Raíz de composición de Envios: el único sitio que crea sus adaptadores y los conecta con el servicio de aplicación. */
@Configuration
@ConditionalOnProperty(name = "tienda.servicio", havingValue = "envios")
class ComposicionDeEnvios {

    @Bean
    EnviosJdbc enviosJdbc(JdbcTemplate jdbc, TransactionTemplate tx) {
        return new EnviosJdbc(jdbc, tx);
    }

    @Bean
    ServicioDeEnvios servicioDeEnvios(EnviosJdbc jdbc) {
        return new ServicioDeEnvios(jdbc, jdbc, jdbc, jdbc);
    }

    @Bean
    EscuchaDeOrdenes escuchaDeOrdenes(ServicioDeEnvios servicio, @Value("${tienda.muere-tras:0}") long muereTras) {
        return new EscuchaDeOrdenes(servicio, muereTras);
    }
}
