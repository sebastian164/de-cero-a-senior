package tienda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AplicacionTienda {
    public static void main(String[] args) { SpringApplication.run(AplicacionTienda.class, args); }
}
