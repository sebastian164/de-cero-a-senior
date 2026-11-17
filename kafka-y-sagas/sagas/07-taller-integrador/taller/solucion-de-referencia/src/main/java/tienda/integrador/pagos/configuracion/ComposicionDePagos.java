package tienda.integrador.pagos.configuracion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tienda.integrador.pagos.adaptadores.entrada.kafka.EscuchaDeOrdenes;
import tienda.integrador.pagos.adaptadores.salida.jdbc.PagosJdbc;
import tienda.integrador.pagos.aplicacion.ServicioDePagos;

/** Raíz de composición de Pagos: el único sitio que crea sus adaptadores y los conecta con el servicio de aplicación. */
@Configuration
@ConditionalOnProperty(name = "tienda.servicio", havingValue = "pagos")
class ComposicionDePagos {

    @Bean
    PagosJdbc pagosJdbc(JdbcTemplate jdbc, TransactionTemplate tx) {
        return new PagosJdbc(jdbc, tx);
    }

    @Bean
    ServicioDePagos servicioDePagos(PagosJdbc jdbc) {
        return new ServicioDePagos(jdbc, jdbc, jdbc, jdbc);
    }

    @Bean
    EscuchaDeOrdenes escuchaDeOrdenes(ServicioDePagos servicio, @Value("${tienda.muere-tras:0}") long muereTras) {
        return new EscuchaDeOrdenes(servicio, muereTras);
    }
}
