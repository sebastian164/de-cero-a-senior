package oficina.reservas.aplicacion;

import oficina.reservas.aplicacion.puertos.RepositorioReservas;
import oficina.reservas.dominio.Reserva;
import oficina.reservas.dominio.ReservaId;
import oficina.reservas.dominio.ReglaIncumplida;

import java.time.Clock;
import java.time.LocalDateTime;

public class CancelarReserva {
    private final RepositorioReservas reservas;
    private final Clock reloj;

    public CancelarReserva(RepositorioReservas reservas, Clock reloj) {
        this.reservas = reservas;
        this.reloj = reloj;
    }

    public void ejecutar(ReservaId id, String quien) {
        Reserva reserva = reservas.buscar(id).orElseThrow(() -> new ReglaIncumplida("La reserva no existe"));
        reserva.cancelar(quien, LocalDateTime.now(reloj));
        reservas.guardar(reserva);
    }
}
