package tienda.integrador.pedidos.configuracion;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tienda.integrador.pedidos.adaptadores.entrada.http.PedidosHttp;
import tienda.integrador.pedidos.adaptadores.entrada.kafka.EscuchaDeResultados;
import tienda.integrador.pedidos.adaptadores.salida.jdbc.PedidosJdbc;
import tienda.integrador.pedidos.aplicacion.ServicioDePedidos;
import tienda.integrador.plataforma.PublicadorDeSalida;

/** Raíz de composición de Pedidos. */
@Configuration
@ConditionalOnProperty(name = "tienda.servicio", havingValue = "pedidos")
class ComposicionDePedidos {

    @Bean
    PedidosJdbc pedidosJdbc(JdbcTemplate jdbc, TransactionTemplate tx) {
        return new PedidosJdbc(jdbc, tx);
    }

    @Bean
    ServicioDePedidos servicioDePedidos(PedidosJdbc jdbc) {
        return new ServicioDePedidos(jdbc, jdbc, jdbc, jdbc);
    }

    @Bean
    EscuchaDeResultados escuchaDeResultados(ServicioDePedidos servicio) {
        return new EscuchaDeResultados(servicio);
    }

    @Bean
    PedidosHttp pedidosHttp(ServicioDePedidos servicio, PublicadorDeSalida salida, @Value("${tienda.http-host:127.0.0.1}") String host, @Value("${tienda.puerto:0}") int puerto) throws IOException {
        return new PedidosHttp(servicio, servicio, salida, host, puerto);
    }
}
