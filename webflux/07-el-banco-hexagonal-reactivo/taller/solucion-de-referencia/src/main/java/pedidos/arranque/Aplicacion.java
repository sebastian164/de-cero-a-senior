package pedidos.arranque;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "pedidos")
public class Aplicacion {
    public static void main(String[] args) {
        System.setProperty("reactor.netty.ioWorkerCount", "4");   // cuatro hilos de bucle
        SpringApplication.run(Aplicacion.class, args);
    }
}
