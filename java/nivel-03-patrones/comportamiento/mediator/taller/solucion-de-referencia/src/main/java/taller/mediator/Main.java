package taller.mediator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;

public class Main {

    public static void main(String[] args) {
        // Paso 1: aviones que se consultan entre sí.
        taller.mediator.sinmediador.Aeropuerto.demostrar().forEach(System.out::println);

        // Pasos 2 y 3: la torre.
        Torre torre = new Torre();
        Avion av1 = new Avion("AV101", torre);
        Avion la2 = new Avion("LA202", torre);
        Avion em3 = new Avion("EM303", torre);
        av1.solicitar(Operacion.ATERRIZAR, false);
        la2.solicitar(Operacion.DESPEGAR, false);
        em3.solicitar(Operacion.ATERRIZAR, true);
        System.out.println("En pista: " + torre.enPista().map(Avion::matricula).orElse("-") + ", cola: " + torre.cola());
        av1.liberarPista();
        av1.liberarPista();
        System.out.println("En pista: " + torre.enPista().map(Avion::matricula).orElse("-") + ", cola: " + torre.cola());
        em3.liberarPista();
        la2.liberarPista();
        torre.bitacora().forEach(System.out::println);
    }
}

enum Operacion { ATERRIZAR, DESPEGAR }

/** Colega: solo conoce a la torre. */
final class Avion {
    private final String matricula;
    private final Torre torre;
    private final List<String> mensajes = new ArrayList<>();

    Avion(String matricula, Torre torre) { this.matricula = matricula; this.torre = torre; }

    String matricula() { return matricula; }
    void solicitar(Operacion op, boolean emergencia) { torre.solicitar(this, op, emergencia); }
    void liberarPista() { torre.liberar(this); }
    void recibir(String mensaje) { mensajes.add(mensaje); }
    List<String> mensajes() { return List.copyOf(mensajes); }
}

/** El mediador: la regla de uso de la pista vive solo aquí. */
final class Torre {
    record Solicitud(Avion avion, Operacion operacion, boolean emergencia, long orden) { }

    /** Paso 3: emergencias primero; entre iguales, aterrizajes antes que despegues; luego orden de llegada. */
    static final Comparator<Solicitud> PRIORIDAD = Comparator
            .comparing((Solicitud s) -> !s.emergencia())
            .thenComparing(s -> s.operacion() != Operacion.ATERRIZAR)
            .thenComparingLong(Solicitud::orden);

    private final PriorityQueue<Solicitud> espera = new PriorityQueue<>(PRIORIDAD);
    private final List<String> bitacora = new ArrayList<>();
    private Solicitud enPista;
    private long contador;

    void solicitar(Avion avion, Operacion op, boolean emergencia) {
        Solicitud s = new Solicitud(avion, op, emergencia, contador++);
        if (enPista == null) {
            autorizar(s);
        } else {
            espera.add(s);
            avion.recibir("En espera, posición " + posicion(s));
            bitacora.add(avion.matricula() + " en espera" + (emergencia ? " (EMERGENCIA)" : ""));
        }
    }

    void liberar(Avion avion) {
        if (enPista == null || enPista.avion() != avion) {
            avion.recibir("No tiene la pista asignada");
            return;
        }
        bitacora.add(avion.matricula() + " liberó la pista");
        enPista = null;
        Solicitud siguiente = espera.poll();
        if (siguiente != null) autorizar(siguiente);
    }

    private void autorizar(Solicitud s) {
        enPista = s;
        s.avion().recibir("Autorizado a " + s.operacion().name().toLowerCase());
        bitacora.add(s.avion().matricula() + " autorizado a " + s.operacion().name().toLowerCase());
    }

    private int posicion(Solicitud s) {
        return (int) espera.stream().filter(o -> PRIORIDAD.compare(o, s) < 0).count() + 1;
    }

    Optional<Avion> enPista() { return Optional.ofNullable(enPista).map(Solicitud::avion); }
    List<String> cola() { return espera.stream().sorted(PRIORIDAD).map(s -> s.avion().matricula()).toList(); }
    List<String> bitacora() { return List.copyOf(bitacora); }
}
