package pedidos.arranque;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.transaction.support.TransactionTemplate;
import pedidos.adaptadores.salida.jdbc.AdaptadorJdbc;
import pedidos.adaptadores.salida.r2dbc.AdaptadorR2dbc;

/** El único lugar que conoce las dos mitades: arma el núcleo con los adaptadores de la opción elegida. */
@Configuration
public class Configuracion {
    // Con un ConnectionFactory de R2DBC en el contexto, Spring Boot no crea el DataSource solo: en A y B se arma a mano.
    @Bean @ConditionalOnExpression("'${opcion}' != 'c'")
    DataSource dataSource(@Value("${spring.datasource.url}") String url, @Value("${spring.datasource.username}") String usuario,
                          @Value("${spring.datasource.password}") String clave, @Value("${spring.datasource.hikari.maximum-pool-size}") int maximo) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url); ds.setUsername(usuario); ds.setPassword(clave); ds.setMaximumPoolSize(maximo);
        return ds;
    }
    @Bean @ConditionalOnExpression("'${opcion}' != 'c'") JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
    @Bean @ConditionalOnExpression("'${opcion}' != 'c'") TransactionTemplate transactionTemplate(DataSource ds) { return new TransactionTemplate(new DataSourceTransactionManager(ds)); }

    @Bean @ConditionalOnProperty(name = "opcion", havingValue = "a")
    pedidos.aplicacion.sincrona.ServicioDePedidos servicioA(JdbcTemplate jdbc, TransactionTemplate tx) { return sincrono(jdbc, tx); }

    @Bean @ConditionalOnProperty(name = "opcion", havingValue = "b")
    pedidos.aplicacion.sincrona.ServicioDePedidos servicioB(JdbcTemplate jdbc, TransactionTemplate tx) { return sincrono(jdbc, tx); }

    private static pedidos.aplicacion.sincrona.ServicioDePedidos sincrono(JdbcTemplate jdbc, TransactionTemplate tx) {
        AdaptadorJdbc a = new AdaptadorJdbc(jdbc, tx);
        return new pedidos.aplicacion.sincrona.ServicioDePedidos(a, a, a);
    }

    @Bean @ConditionalOnProperty(name = "opcion", havingValue = "c")
    pedidos.aplicacion.reactiva.ServicioDePedidos servicioC(DatabaseClient db, TransactionalOperator tx) {
        AdaptadorR2dbc a = new AdaptadorR2dbc(db, tx);
        return new pedidos.aplicacion.reactiva.ServicioDePedidos(a, a, a);
    }
}
