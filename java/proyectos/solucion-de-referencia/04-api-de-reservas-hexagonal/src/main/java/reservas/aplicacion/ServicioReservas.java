package reservas.aplicacion;

import reservas.aplicacion.Puertos.*;
import reservas.dominio.Franja;
import reservas.dominio.Reserva;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Casos de uso. Conoce el dominio y los puertos; no sabe de HTTP, SQL ni Spring. */
public class ServicioReservas {
    public record Actor(String usuario, boolean admin) {}

    private final RepositorioReservas repo;
    private final Transaccion tx;
    private final Clock reloj;

    public ServicioReservas(RepositorioReservas repo, Transaccion tx, Clock reloj) {
        this.repo = repo; this.tx = tx; this.reloj = reloj;
    }

    public Reserva reservar(Actor actor, String claveIdempotencia, String espacio, Franja franja) {
        if (claveIdempotencia != null) {
            Optional<Reserva> previa = repo.porClave(claveIdempotencia, actor.usuario());
            if (previa.isPresent()) return previa.get();
        }
        try {
            return tx.ejecutar(() -> {
                Reserva r = Reserva.nueva(espacio, actor.usuario(), franja, reloj.instant());
                repo.guardar(r);
                if (claveIdempotencia != null) repo.asociarClave(claveIdempotencia, actor.usuario(), r.id());
                return r;
            });
        } catch (Reserva.FranjaOcupada ocupada) {
            // Dos peticiones simultáneas con la misma clave: la perdedora ve el resultado de la ganadora.
            if (claveIdempotencia != null) {
                Optional<Reserva> ganadora = repo.porClave(claveIdempotencia, actor.usuario());
                if (ganadora.isPresent()) return ganadora.get();
            }
            throw ocupada;
        }
    }

    public Reserva ver(Actor actor, UUID id) {
        Reserva r = repo.buscar(id).orElseThrow(() -> new Reserva.NoEncontrada(id));
        if (!actor.admin() && !r.esDe(actor.usuario())) throw new Reserva.SinPermiso();
        return r;
    }

    public Pagina<Reserva> listar(Actor actor, int pagina, int tamano) {
        return repo.listar(actor.admin() ? null : actor.usuario(), pagina, tamano);
    }

    /** Cancela y, en la misma transacción, entrega la franja al primero de la lista de espera. */
    public void cancelar(Actor actor, UUID id) {
        tx.ejecutar(() -> {
            Reserva r = ver(actor, id);
            r.cancelar(reloj.instant());
            repo.actualizar(r);
            repo.primeroEnEspera(r.espacio(), r.franja()).ifPresent(e -> {
                repo.guardar(Reserva.nueva(e.espacio(), e.usuario(), e.franja(), reloj.instant()));
                repo.quitarDeEspera(e.id());
            });
            return null;
        });
    }

    public record Disponibilidad(String espacio, List<Franja> ocupadas, List<Franja.Hueco> libres) {}

    /** Disponibilidad por franjas de un espacio en una ventana: qué está ocupado y qué queda libre. */
    public Disponibilidad disponibilidad(String espacio, Instant desde, Instant hasta) {
        List<Franja> ocupadas = repo.ocupadas(espacio, desde, hasta);
        return new Disponibilidad(espacio, ocupadas, Franja.huecosLibres(desde, hasta, ocupadas));
    }

    public void esperar(Actor actor, String espacio, Franja franja) {
        repo.esperar(espacio, actor.usuario(), franja);
    }
}
