package tienda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Una sola aplicación, cuatro servicios: --tienda.servicio=orquestador|pagos|inventario|envios. Cada uno, su base y su grupo. */
@SpringBootApplication
@EnableScheduling
public class Aplicacion {
    public static void main(String[] args) {
        SpringApplication.run(Aplicacion.class, args);
    }
}
