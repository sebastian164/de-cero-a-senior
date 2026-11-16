package oficina.reservas;

import oficina.reservas.aplicacion.puertos.RepositorioReservas;
import oficina.reservas.dominio.Intervalo;
import oficina.reservas.dominio.Reserva;
import oficina.reservas.dominio.ReservaId;
import oficina.reservas.dominio.SalaId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Las dos implementaciones del puerto deben cumplir el mismo contrato. */
abstract class ContratoRepositorioReservas {

    abstract RepositorioReservas repositorio();

    static final LocalDateTime NUEVE = LocalDateTime.of(2030, 3, 4, 9, 0);

    @Test
    void guardaBuscaYFiltraPorSalaVentanaYEstado() {
        var repo = repositorio();
        var a = new Reserva(ReservaId.nueva(), new SalaId("azul"), "ana", new Intervalo(NUEVE, NUEVE.plusHours(1)), Reserva.Estado.ACTIVA);
        var b = new Reserva(ReservaId.nueva(), new SalaId("azul"), "luis", new Intervalo(NUEVE.plusHours(2), NUEVE.plusHours(3)), Reserva.Estado.CANCELADA);
        var c = new Reserva(ReservaId.nueva(), new SalaId("roja"), "eva", new Intervalo(NUEVE, NUEVE.plusHours(1)), Reserva.Estado.ACTIVA);
        List.of(a, b, c).forEach(repo::guardar);

        assertThat(repo.buscar(a.id())).get().extracting(Reserva::usuario).isEqualTo("ana");
        assertThat(repo.activasDeSalaEntre(new SalaId("azul"), NUEVE.minusHours(1), NUEVE.plusHours(5)))
                .extracting(Reserva::id).containsExactly(a.id());
        // ventana que termina justo cuando empieza la reserva: no la toca
        assertThat(repo.activasDeSalaEntre(new SalaId("azul"), NUEVE.minusHours(1), NUEVE)).isEmpty();
    }
}
