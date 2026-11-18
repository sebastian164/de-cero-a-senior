package banco.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Aplicacion {

    public static void main(String[] args) {
        // Cuatro hilos de bucle de eventos, para que la medición dé lo mismo en cualquier máquina.
        // Sin esto, Netty usa tantos como núcleos.
        System.setProperty("reactor.netty.ioWorkerCount", "4");
        SpringApplication.run(Aplicacion.class, args);
    }
}
