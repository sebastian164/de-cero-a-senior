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

    @Bean
    CommandLineRunner demostracion(ReactiveMongoTemplate mongo, Escritor escritor, NotificadorPorFlujoDeCambios notificador,
                                   ContadorDeComandos comandos) {
        return args -> {
            mongo.dropCollection(Movimiento.class).then(mongo.createCollection(Movimiento.class)).block();
            List<Long> latencias = new CopyOnWriteArrayList<>();
            comandos.reiniciar();

            // 1. La misma medición que con sondeo
            Disposable suscripcion = notificador.avisos()
                    .subscribe(movimiento -> latencias.add(Duration.between(movimiento.fecha(), Instant.now()).toMillis()));
            Thread.sleep(1_000);                                  // el cursor del flujo se abre de forma asíncrona
            for (int i = 1; i <= 5; i++) {
                Thread.sleep(700);
                escritor.registrar("cuenta-" + i, 10_000L * i).block();
            }
            Thread.sleep(2_000);
            suscripcion.dispose();

            System.out.println("Flujo de cambios");
            System.out.println("Avisos recibidos:        " + latencias.size() + " de 5");
            System.out.println("Latencia de cada aviso:  " + latencias + " ms");
            System.out.println("Latencia media:          " + latencias.stream().mapToLong(Long::longValue).average().orElse(0) + " ms");
            System.out.println("Comandos a MongoDB:      " + comandos.resumen());

            // 2. Una caída: el notificador se detiene, llegan dos movimientos, y vuelve con la última ficha
            escritor.registrar("cuenta-6", 60_000).block();
            escritor.registrar("cuenta-7", 70_000).block();
            List<String> recuperados = notificador.avisos()
                    .take(2)
                    .map(Movimiento::cuentaId)
                    .collectList()
                    .block(Duration.ofSeconds(5));
            System.out.println();
            System.out.println("Escritos mientras el notificador estaba caído: cuenta-6, cuenta-7");
            System.out.println("Recibidos al reanudar con la última ficha:     " + recuperados);
        };
    }
}
