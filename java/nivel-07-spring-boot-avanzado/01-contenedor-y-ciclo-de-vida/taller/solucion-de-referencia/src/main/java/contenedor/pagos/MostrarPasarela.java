package contenedor.pagos;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class MostrarPasarela implements ApplicationRunner {
    private final PasarelaPagos pasarela;
    MostrarPasarela(PasarelaPagos pasarela) { this.pasarela = pasarela; }
    @Override public void run(ApplicationArguments args) { System.out.println("Pasarela activa: " + pasarela.nombre()); }
}
