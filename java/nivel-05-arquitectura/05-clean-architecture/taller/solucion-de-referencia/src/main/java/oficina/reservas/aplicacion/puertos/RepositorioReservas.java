package oficina.reservas.aplicacion.puertos;

import oficina.reservas.dominio.Reserva;
import oficina.reservas.dominio.ReservaId;
import oficina.reservas.dominio.SalaId;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Puerto de salida declarado por el círculo de casos de uso. */
public interface RepositorioReservas {
    void guardar(Reserva reserva);
    Optional<Reserva> buscar(ReservaId id);
    /** Reservas activas de la sala que tocan [desde, hasta). */
    List<Reserva> activasDeSalaEntre(SalaId sala, LocalDateTime desde, LocalDateTime hasta);
}
