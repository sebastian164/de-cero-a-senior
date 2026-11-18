package avisos;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.ChangeStreamOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;

@SpringBootApplication
public class Aplicacion {
    public static void main(String[] args) { SpringApplication.run(Aplicacion.class, args); }

    @Bean
    CommandLineRunner modo(ReactiveMongoTemplate mongo, ContadorDeComandos comandos, @Value("${modo}") String modo,
                           @Value("${cambios}") int cambios, @Value("${intervalo-ms}") long intervalo, @Value("${prefijo}") String prefijo,
                           @Value("${indice-unico}") boolean indiceUnico, @Value("${parada-tras-aviso}") int parada,
                           @Value("${pausa-tras-aviso}") int pausaTras, @Value("${pausa-ms}") long pausaMs, ConfigurableApplicationContext contexto) {
        return args -> {
            switch (modo) {
                case "confirmador" -> {
                    long n = new Confirmador(mongo).correr(prefijo, cambios, intervalo).block();
                    System.out.println("CONFIRMADOR: " + n + " cambios de estado hechos");
                    System.exit(0);
                }
                case "notificador" -> {
                    Notificador notificador = new Notificador(mongo, indiceUnico, parada, false, pausaTras, pausaMs);
                    notificador.prepararIndice().block();
                    notificador.iniciar();
                    // Al cerrar el contexto (Ctrl+C o SIGTERM) y ANTES de que se cierre el cliente de MongoDB: detención ordenada.
                    contexto.addApplicationListener((ApplicationListener<ContextClosedEvent>) ev -> notificador.detener());
                    System.out.println("NOTIFICADOR LISTO");
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("COMANDOS: " + comandos.resumen())));
                    new CountDownLatch(1).await();
                }
                case "sondeo" -> {
                    new NotificadorPorSondeo(mongo).flujo(Duration.ofMillis(500)).subscribe();
                    System.out.println("SONDEO LISTO");
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("COMANDOS: " + comandos.resumen())));
                    new CountDownLatch(1).await();
                }
                default -> { }   // servicio: solo el servidor web
            }
        };
    }

    /** Paso 7: GET /pedidos/{id}/estados, un evento SSE por cada cambio de estado de ese pedido (filtrado en el servidor). */
    @Bean
    @ConditionalOnProperty(name = "modo", havingValue = "servicio")
    RouterFunction<ServerResponse> rutas(ReactiveMongoTemplate mongo) {
        return RouterFunctions.route().GET("/pedidos/{id}/estados", r -> {
            String id = r.pathVariable("id");
            var opciones = ChangeStreamOptions.builder().filter(newAggregation(match(where("operationType").is("update")
                    .and("documentKey._id").is(id).and("updateDescription.updatedFields.estado").exists(true)))).build();
            Flux<String> estados = mongo.changeStream("pedidos", opciones, Document.class)
                    .map(e -> e.getRaw().getUpdateDescription().getUpdatedFields().getString("estado").getValue());
            return ServerResponse.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(estados, String.class);
        }).build();
    }
}
