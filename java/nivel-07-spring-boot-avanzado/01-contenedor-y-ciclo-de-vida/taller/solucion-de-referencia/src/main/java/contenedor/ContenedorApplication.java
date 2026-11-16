package contenedor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ContenedorApplication {
    public static void main(String[] args) { SpringApplication.run(ContenedorApplication.class, args); }
}
