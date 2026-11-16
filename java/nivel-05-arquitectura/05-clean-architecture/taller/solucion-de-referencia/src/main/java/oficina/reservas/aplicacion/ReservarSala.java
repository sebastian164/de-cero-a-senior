package oficina.reservas.aplicacion;

import oficina.reservas.aplicacion.puertos.RepositorioReservas;
import oficina.reservas.dominio.Intervalo;
import oficina.reservas.dominio.Reserva;
import oficina.reservas.dominio.ReservaId;
import oficina.reservas.dominio.SalaId;

import java.time.LocalDateTime;

public class ReservarSala {
    public record Comando(String sala, String usuario, LocalDateTime inicio, LocalDateTime fin) { }

    private final RepositorioReservas reservas;

    public ReservarSala(RepositorioReservas reservas) { this.reservas = reservas; }

    public ReservaId ejecutar(Comando c) {
        SalaId sala = new SalaId(c.sala());
        Intervalo intervalo = new Intervalo(c.inicio(), c.fin());
        Reserva reserva = Reserva.nueva(sala, c.usuario(), intervalo,
                reservas.activasDeSalaEntre(sala, intervalo.inicio(), intervalo.fin()));
        reservas.guardar(reserva);
        return reserva.id();
    }
}
