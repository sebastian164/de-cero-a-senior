package pedidos.arranque;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import pedidos.adaptadores.entrada.Rutas;
import pedidos.adaptadores.salida.*;
import pedidos.aplicacion.ServicioDePedidos;

@SpringBootApplication
public class Aplicacion {
    public static void main(String[] args) {
        System.setProperty("reactor.netty.ioWorkerCount", "4");   // cuatro hilos de bucle
        SpringApplication.run(Aplicacion.class, args);
    }

    @Bean ServicioDePedidos servicio(DatabaseClient db, TransactionalOperator tx, WebClient.Builder web,
                                     @Value("${inventario.url}") String inventario, @Value("${pagos.url}") String pagos) {
        AdaptadorR2dbc r2dbc = new AdaptadorR2dbc(db, tx);
        return new ServicioDePedidos(r2dbc, r2dbc, r2dbc, new ClientesExternos.ClienteDeInventario(web, inventario),
                new ClientesExternos.ClienteDePagos(web, pagos), new EventosEnMemoria());
    }

    @Bean RouterFunction<ServerResponse> rutas(ServicioDePedidos servicio) { return Rutas.rutas(servicio); }
}
