package banco.avisos;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.Disposable;

@SpringBootApplication
public class Aplicacion {

    public static void main(String[] args) {
        SpringApplication.run(Aplicacion.class, args);
    }

    /** Escribe cinco movimientos, uno cada 700 ms, y mide cuánto tarda el notificador en enterarse de cada uno. */
    @Bean
    CommandLineRunner demostracion(ReactiveMongoTemplate mongo, Escritor escritor, NotificadorPorSondeo notificador,
                                   ContadorDeComandos comandos) {
        return args -> {
            mongo.dropCollection(Movimiento.class).then(mongo.createCollection(Movimiento.class)).block();
            List<Long> latencias = new CopyOnWriteArrayList<>();
            comandos.reiniciar();

            Disposable suscripcion = notificador.avisos()
                    .subscribe(movimiento -> latencias.add(Duration.between(movimiento.fecha(), Instant.now()).toMillis()));
            for (int i = 1; i <= 5; i++) {
                Thread.sleep(700);                                // el hilo main no es un bucle de eventos: puede dormir
                escritor.registrar("cuenta-" + i, 10_000L * i).block();
            }
            Thread.sleep(2_000);                                  // margen para que llegue el último aviso
            suscripcion.dispose();

            System.out.println("Sondeo cada " + NotificadorPorSondeo.CADA.toMillis() + " ms");
            System.out.println("Avisos recibidos:        " + latencias.size() + " de 5");
            System.out.println("Latencia de cada aviso:  " + latencias + " ms");
            System.out.println("Latencia media:          " + latencias.stream().mapToLong(Long::longValue).average().orElse(0) + " ms");
            System.out.println("Comandos a MongoDB:      " + comandos.resumen());
        };
    }
}
