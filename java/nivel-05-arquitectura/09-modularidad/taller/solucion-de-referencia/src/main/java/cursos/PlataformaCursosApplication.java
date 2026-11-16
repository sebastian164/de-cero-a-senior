package cursos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class PlataformaCursosApplication {
    public static void main(String[] args) { SpringApplication.run(PlataformaCursosApplication.class, args); }
}
