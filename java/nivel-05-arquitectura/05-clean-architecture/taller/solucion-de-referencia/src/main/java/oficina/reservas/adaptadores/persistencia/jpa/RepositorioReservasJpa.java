package oficina.reservas.adaptadores.persistencia.jpa;

import oficina.reservas.aplicacion.puertos.RepositorioReservas;
import oficina.reservas.dominio.Reserva;
import oficina.reservas.dominio.ReservaId;
import oficina.reservas.dominio.SalaId;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class RepositorioReservasJpa implements RepositorioReservas {
    private final ReservaJpaRepository jpa;

    RepositorioReservasJpa(ReservaJpaRepository jpa) { this.jpa = jpa; }

    @Override public void guardar(Reserva reserva) { jpa.save(ReservaMapper.aEntidad(reserva)); }
    @Override public Optional<Reserva> buscar(ReservaId id) { return jpa.findById(id.valor()).map(ReservaMapper::aDominio); }

    @Override
    public List<Reserva> activasDeSalaEntre(SalaId sala, LocalDateTime desde, LocalDateTime hasta) {
        return jpa.activasQueTocan(sala.valor(), desde, hasta).stream().map(ReservaMapper::aDominio).toList();
    }
}
