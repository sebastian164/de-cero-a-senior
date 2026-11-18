package acortador;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AplicacionAcortador {
    public static void main(String[] args) {
        SpringApplication.run(AplicacionAcortador.class, args);
    }
}
