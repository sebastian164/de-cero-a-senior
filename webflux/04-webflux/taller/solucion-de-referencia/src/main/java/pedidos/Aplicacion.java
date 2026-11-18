package pedidos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Aplicacion {
    public static void main(String[] args) {
        System.setProperty("reactor.netty.ioWorkerCount", "4");   // cuatro hilos de bucle
        SpringApplication.run(Aplicacion.class, args);
    }
}
