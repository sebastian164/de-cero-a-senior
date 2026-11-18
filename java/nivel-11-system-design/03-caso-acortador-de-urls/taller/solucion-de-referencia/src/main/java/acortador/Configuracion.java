package acortador;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class Configuracion {
    @Bean
    GeneradorDeCodigos generador(Propiedades p, CoordinadorDeRangos coordinador) {
        return switch (p.estrategia()) {
            case "rangos" -> new GeneradorPorRangos(coordinador, p.tamanoRango(), new PermutacionConClave(p.claveSecreta()));
            case "aleatorio" -> new GeneradorAleatorio();
            default -> throw new IllegalArgumentException("estrategia desconocida: " + p.estrategia());
        };
    }
}
