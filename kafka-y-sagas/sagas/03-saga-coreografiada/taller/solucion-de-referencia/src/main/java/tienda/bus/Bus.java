package tienda.bus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Un bus en memoria. Nadie sabe quién escucha lo que publica: esa es la coreografía. */
public final class Bus {

    /** Paso 7: ningún flujo legítimo de la tienda pasa de 10 eventos encadenados. */
    public static final int PROFUNDIDAD_MAXIMA = 10;

    private final Map<String, List<Consumer<Evento>>> suscriptores = new HashMap<>();
    private final Deque<Evento> pendientes = new ArrayDeque<>();
    public final List<Evento> traza = new ArrayList<>();
    public final List<Evento> descartados = new ArrayList<>();
    private int secuencia;

    public void suscribir(String tipo, Consumer<Evento> reaccion) {
        suscriptores.computeIfAbsent(tipo, t -> new ArrayList<>()).add(reaccion);
    }

    public void publicar(String tipo, String correlacion) {
        encolar(new Evento("e-" + ++secuencia, tipo, correlacion, null, 0));
    }

    /** Publica un evento causado por otro: hereda la correlación y suma uno a la profundidad. */
    public void publicar(String tipo, Evento causa) {
        encolar(new Evento("e-" + ++secuencia, tipo, causa.correlacion(), causa.id(), causa.profundidad() + 1));
    }

    private void encolar(Evento evento) {
        if (evento.profundidad() > PROFUNDIDAD_MAXIMA) {
            descartados.add(evento);                // defensa contra ciclos: se aparta y se avisa
            return;
        }
        pendientes.addLast(evento);
    }

    /** Entrega todo lo pendiente. Devuelve cuántos eventos se entregaron. */
    public int entregar() {
        int entregados = 0;
        while (!pendientes.isEmpty()) {
            Evento evento = pendientes.pollFirst();
            traza.add(evento);
            entregados++;
            suscriptores.getOrDefault(evento.tipo(), List.of()).forEach(reaccion -> reaccion.accept(evento));
        }
        return entregados;
    }
}
