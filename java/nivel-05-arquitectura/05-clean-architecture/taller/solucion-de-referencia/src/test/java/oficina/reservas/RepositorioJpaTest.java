package oficina.reservas;

import oficina.reservas.adaptadores.persistencia.jpa.RepositorioReservasJpa;
import oficina.reservas.aplicacion.CancelarReserva;
import oficina.reservas.aplicacion.ReservarSala;
import oficina.reservas.aplicacion.puertos.RepositorioReservas;
import oficina.reservas.dominio.ReglaIncumplida;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El adaptador JPA contra una base real (H2) y los casos de uso ensamblados por Spring. */
@SpringBootTest
class RepositorioJpaTest extends ContratoRepositorioReservas {
    @Autowired RepositorioReservasJpa repo;
    @Autowired ReservarSala reservar;
    @Autowired CancelarReserva cancelar;

    @Override RepositorioReservas repositorio() { return repo; }

    @Test
    void elSolapamientoSeDetectaLeyendoDeLaBase() {
        LocalDateTime inicio = LocalDateTime.of(2031, 1, 1, 10, 0);
        reservar.ejecutar(new ReservarSala.Comando("verde", "ana", inicio, inicio.plusHours(2)));
        assertThatThrownBy(() -> reservar.ejecutar(new ReservarSala.Comando("verde", "luis", inicio.plusHours(1), inicio.plusHours(3))))
                .isInstanceOf(ReglaIncumplida.class);
    }
}
