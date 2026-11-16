package oficina.reservas.adaptadores.persistencia.memoria;

import oficina.reservas.aplicacion.puertos.RepositorioReservas;
import oficina.reservas.dominio.Intervalo;
import oficina.reservas.dominio.Reserva;
import oficina.reservas.dominio.ReservaId;
import oficina.reservas.dominio.SalaId;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class RepositorioReservasEnMemoria implements RepositorioReservas {
    private final Map<ReservaId, Reserva> datos = new ConcurrentHashMap<>();

    @Override public void guardar(Reserva reserva) { datos.put(reserva.id(), reserva); }
    @Override public Optional<Reserva> buscar(ReservaId id) { return Optional.ofNullable(datos.get(id)); }

    @Override
    public List<Reserva> activasDeSalaEntre(SalaId sala, LocalDateTime desde, LocalDateTime hasta) {
        Intervalo ventana = new Intervalo(desde, hasta);
        return datos.values().stream()
                .filter(r -> r.sala().equals(sala) && r.activa() && r.intervalo().seSolapaCon(ventana))
                .toList();
    }
}
