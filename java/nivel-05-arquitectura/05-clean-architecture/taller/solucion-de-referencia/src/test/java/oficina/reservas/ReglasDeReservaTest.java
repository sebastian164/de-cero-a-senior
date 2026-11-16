package oficina.reservas;

import oficina.reservas.adaptadores.persistencia.memoria.RepositorioReservasEnMemoria;
import oficina.reservas.aplicacion.CancelarReserva;
import oficina.reservas.aplicacion.ConsultarDisponibilidad;
import oficina.reservas.aplicacion.ReservarSala;
import oficina.reservas.dominio.Intervalo;
import oficina.reservas.dominio.ReglaIncumplida;
import oficina.reservas.dominio.ReservaId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Todas las reglas, sin Spring: casos de uso + adaptador en memoria + reloj fijo. */
class ReglasDeReservaTest {

    static final LocalDateTime AHORA = LocalDateTime.of(2030, 5, 20, 8, 0);
    static final LocalDateTime DIEZ = AHORA.withHour(10);

    RepositorioReservasEnMemoria repo;
    ReservarSala reservar;
    CancelarReserva cancelar;
    ConsultarDisponibilidad disponibilidad;

    @BeforeEach
    void preparar() {
        repo = new RepositorioReservasEnMemoria();
        reservar = new ReservarSala(repo);
        cancelar = new CancelarReserva(repo, Clock.fixed(AHORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        disponibilidad = new ConsultarDisponibilidad(repo);
    }

    ReservaId reservar(String quien, LocalDateTime inicio, LocalDateTime fin) {
        return reservar.ejecutar(new ReservarSala.Comando("azul", quien, inicio, fin));
    }

    @Test void duracionMinimaYMaximaSonInclusivas() {
        reservar("ana", DIEZ, DIEZ.plusMinutes(30));
        reservar("ana", DIEZ.plusHours(1), DIEZ.plusHours(5));
        assertThat(repo.activasDeSalaEntre(new oficina.reservas.dominio.SalaId("azul"), DIEZ, DIEZ.plusHours(6))).hasSize(2);
    }

    @Test void rechazaMenosDe30Minutos() {
        assertThatThrownBy(() -> reservar("ana", DIEZ, DIEZ.plusMinutes(29))).isInstanceOf(ReglaIncumplida.class);
    }

    @Test void rechazaMasDe4Horas() {
        assertThatThrownBy(() -> reservar("ana", DIEZ, DIEZ.plusHours(4).plusMinutes(1))).isInstanceOf(ReglaIncumplida.class);
    }

    @Test void rechazaSolapamientoEnLaMismaSala() {
        reservar("ana", DIEZ, DIEZ.plusHours(2));
        assertThatThrownBy(() -> reservar("luis", DIEZ.plusHours(1), DIEZ.plusHours(3)))
                .isInstanceOf(ReglaIncumplida.class).hasMessageContaining("ya está reservada");
    }

    @Test void reservasContiguasNoSeSolapan() {
        reservar("ana", DIEZ, DIEZ.plusHours(1));
        reservar("luis", DIEZ.plusHours(1), DIEZ.plusHours(2));
    }

    @Test void otraSalaNoChoca() {
        reservar("ana", DIEZ, DIEZ.plusHours(1));
        reservar.ejecutar(new ReservarSala.Comando("roja", "luis", DIEZ, DIEZ.plusHours(1)));
    }

    @Test void soloQuienReservoCancela() {
        ReservaId id = reservar("ana", DIEZ, DIEZ.plusHours(1));
        assertThatThrownBy(() -> cancelar.ejecutar(id, "luis")).hasMessage("Solo quien reservó puede cancelar");
        cancelar.ejecutar(id, "ana");
    }

    @Test void cancelaHastaUnaHoraAntes() {
        ReservaId justo = reservar("ana", AHORA.plusHours(1), AHORA.plusHours(2));     // exactamente 1 h antes: se permite
        cancelar.ejecutar(justo, "ana");
        ReservaId tarde = reservar("ana", AHORA.plusMinutes(59), AHORA.plusMinutes(120));
        assertThatThrownBy(() -> cancelar.ejecutar(tarde, "ana")).hasMessageContaining("una hora antes");
    }

    @Test void unaReservaCanceladaLiberaElHorario() {
        ReservaId id = reservar("ana", DIEZ, DIEZ.plusHours(2));
        cancelar.ejecutar(id, "ana");
        reservar("luis", DIEZ, DIEZ.plusHours(2));
    }

    @Test void disponibilidadDevuelveLosHuecos() {
        reservar("ana", DIEZ, DIEZ.plusHours(1));
        reservar("luis", DIEZ.plusHours(2), DIEZ.plusHours(3));
        assertThat(disponibilidad.ejecutar("azul", AHORA, AHORA.withHour(18))).containsExactly(
                new Intervalo(AHORA, DIEZ),
                new Intervalo(DIEZ.plusHours(1), DIEZ.plusHours(2)),
                new Intervalo(DIEZ.plusHours(3), AHORA.withHour(18)));
    }
}
