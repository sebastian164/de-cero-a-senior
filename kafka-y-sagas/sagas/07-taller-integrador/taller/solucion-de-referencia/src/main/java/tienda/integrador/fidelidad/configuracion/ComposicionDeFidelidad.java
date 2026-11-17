package tienda.integrador.fidelidad.configuracion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import tienda.integrador.fidelidad.adaptadores.entrada.kafka.EscuchaDeResultados;
import tienda.integrador.fidelidad.adaptadores.salida.jdbc.FidelidadJdbc;
import tienda.integrador.fidelidad.aplicacion.ServicioDeFidelidad;

/** Raíz de composición de Fidelidad. */
@Configuration
@ConditionalOnProperty(name = "tienda.servicio", havingValue = "fidelidad")
class ComposicionDeFidelidad {

    @Bean
    FidelidadJdbc fidelidadJdbc(JdbcTemplate jdbc) {
        return new FidelidadJdbc(jdbc);
    }

    @Bean
    ServicioDeFidelidad servicioDeFidelidad(FidelidadJdbc jdbc) {
        return new ServicioDeFidelidad(jdbc);
    }

    @Bean
    EscuchaDeResultados escuchaDeResultados(ServicioDeFidelidad servicio) {
        return new EscuchaDeResultados(servicio);
    }
}
