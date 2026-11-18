import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import reactor.core.publisher.Mono;

public class Main {

    public static void main(String[] args) {
        RepositorioDeMovimientos repositorio = new RepositorioDeMovimientos();
        ServicioDeTasas tasas = new ServicioDeTasas(Duration.ofMillis(100));
        ServicioDeMovimientos servicio = new ServicioDeMovimientos(repositorio, tasas);

        Mono<Movimiento> pendiente = servicio.registrar("cuenta-1", 10_000);
        System.out.println("Tras ensamblar:  llamadas remotas = " + tasas.llamadasRemotas() + ", guardados = " + repositorio.cuantos());
        Movimiento primero = pendiente.block();
        System.out.println("Tras suscribir:  llamadas remotas = " + tasas.llamadasRemotas() + ", guardados = " + repositorio.cuantos());

        List<Movimiento> devueltos = new ArrayList<>(List.of(primero));
        for (int i = 2; i <= 3; i++) {
            devueltos.add(servicio.registrar("cuenta-" + i, 10_000).block());
        }

        System.out.println("Devueltos por el servicio:     " + devueltos.size() + " movimientos");
        System.out.println("Primero:                       " + devueltos.getFirst());
        System.out.println("Guardados en el repositorio:   " + repositorio.cuantos());
        System.out.println("Llamadas remotas a las tasas:  " + tasas.llamadasRemotas());

        servicio.registrar("cuenta-1", 0)
                .doOnError(error -> System.out.println("Valor cero:                    error -> " + error.getMessage()))
                .onErrorResume(error -> Mono.empty())
                .block();
    }
}
