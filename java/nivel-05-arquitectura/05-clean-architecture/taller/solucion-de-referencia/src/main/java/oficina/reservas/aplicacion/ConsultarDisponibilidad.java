package oficina.reservas.aplicacion;

import oficina.reservas.aplicacion.puertos.RepositorioReservas;
import oficina.reservas.dominio.Intervalo;
import oficina.reservas.dominio.Reserva;
import oficina.reservas.dominio.SalaId;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Devuelve los huecos libres de una sala dentro de una ventana. */
public class ConsultarDisponibilidad {
    private final RepositorioReservas reservas;

    public ConsultarDisponibilidad(RepositorioReservas reservas) { this.reservas = reservas; }

    public List<Intervalo> ejecutar(String sala, LocalDateTime desde, LocalDateTime hasta) {
        List<Intervalo> ocupados = reservas.activasDeSalaEntre(new SalaId(sala), desde, hasta).stream()
                .map(Reserva::intervalo)
                .sorted(Comparator.comparing(Intervalo::inicio))
                .toList();
        List<Intervalo> libres = new ArrayList<>();
        LocalDateTime cursor = desde;
        for (Intervalo ocupado : ocupados) {
            if (ocupado.inicio().isAfter(cursor)) libres.add(new Intervalo(cursor, ocupado.inicio()));
            if (ocupado.fin().isAfter(cursor)) cursor = ocupado.fin();
        }
        if (cursor.isBefore(hasta)) libres.add(new Intervalo(cursor, hasta));
        return libres;
    }
}
