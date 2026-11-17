package tienda.integrador.orquestador.configuracion;

import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tienda.integrador.orquestador.adaptadores.entrada.http.AdminHttp;
import tienda.integrador.orquestador.adaptadores.entrada.kafka.EscuchaDePedidos;
import tienda.integrador.orquestador.adaptadores.entrada.kafka.EscuchaDeRespuestas;
import tienda.integrador.orquestador.adaptadores.entrada.reloj.RelojDeSagas;
import tienda.integrador.orquestador.adaptadores.salida.jdbc.SagasJdbc;
import tienda.integrador.orquestador.aplicacion.ServicioDeSagas;
import tienda.integrador.plataforma.PublicadorDeSalida;

/** Raíz de composición del orquestador: el único sitio que crea sus adaptadores y los conecta con el servicio de aplicación. */
@Configuration
@ConditionalOnProperty(name = "tienda.servicio", havingValue = "orquestador")
class ComposicionDelOrquestador {

    @Bean
    SagasJdbc sagasJdbc(JdbcTemplate jdbc, TransactionTemplate tx) {
        return new SagasJdbc(jdbc, tx);
    }

    @Bean
    ServicioDeSagas servicioDeSagas(SagasJdbc jdbc, @Value("${tienda.espera-ms:3000}") long esperaMs, @Value("${tienda.intentos-de-compensacion:8}") int intentos) {
        return new ServicioDeSagas(jdbc, jdbc, jdbc, jdbc, Duration.ofMillis(esperaMs), intentos);
    }

    @Bean
    EscuchaDeRespuestas escuchaDeRespuestas(ServicioDeSagas servicio, @Value("${tienda.muere-tras:0}") long muereTras) {
        return new EscuchaDeRespuestas(servicio, muereTras);
    }

    @Bean
    EscuchaDePedidos escuchaDePedidos(ServicioDeSagas servicio) {
        return new EscuchaDePedidos(servicio);
    }

    @Bean
    RelojDeSagas relojDeSagas(ServicioDeSagas servicio) {
        return new RelojDeSagas(servicio);
    }

    @Bean
    AdminHttp adminHttp(ServicioDeSagas servicio, PublicadorDeSalida salida, @Value("${tienda.http-host:127.0.0.1}") String host, @Value("${tienda.puerto:0}") int puerto) throws IOException {
        return new AdminHttp(servicio, salida, host, puerto);
    }
}
