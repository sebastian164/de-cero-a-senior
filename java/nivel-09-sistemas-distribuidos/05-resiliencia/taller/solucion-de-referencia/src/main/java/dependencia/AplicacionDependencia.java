package dependencia;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Una dependencia externa simulada (se arranca tres veces: crítica, recomendaciones y reseñas). */
@SpringBootApplication
public class AplicacionDependencia {

    /** Lo que la prueba puede cambiar en caliente. */
    public static class Control {
        public volatile long retrasoMs = 0;
        public volatile boolean falla = false;
        public final AtomicInteger llamadas = new AtomicInteger();
    }

    @Bean
    Control control() {
        return new Control();
    }

    @RestController
    static class Datos {
        final Control control;
        Datos(Control control) { this.control = control; }

        @GetMapping("/datos/{id}")
        Map<String, Object> datos(@PathVariable long id) throws InterruptedException {
            control.llamadas.incrementAndGet();
            if (control.retrasoMs > 0) {
                Thread.sleep(control.retrasoMs);
            }
            if (control.falla) {
                throw new IllegalStateException("la dependencia falló");
            }
            return Map.of("id", id, "valor", "ok");
        }
    }
}
