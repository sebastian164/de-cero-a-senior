package banco.arranque;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"banco.arranque", "banco.adaptadores"})
public class AplicacionBanco {

    public static void main(String[] args) {
        System.setProperty("reactor.netty.ioWorkerCount", "4");  // cuatro hilos de bucle, en cualquier máquina
        SpringApplication.run(AplicacionBanco.class, args);
    }
}
