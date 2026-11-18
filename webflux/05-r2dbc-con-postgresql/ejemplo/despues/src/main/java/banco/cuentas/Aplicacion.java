package banco.cuentas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Aplicacion {

    public static void main(String[] args) {
        System.setProperty("reactor.netty.ioWorkerCount", "4");  // cuatro hilos de bucle, en cualquier máquina
        SpringApplication.run(Aplicacion.class, args);
    }
}
