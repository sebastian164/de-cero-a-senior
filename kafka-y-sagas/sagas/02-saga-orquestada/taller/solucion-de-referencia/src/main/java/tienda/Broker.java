package tienda;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Colas por destino que sobreviven a la caída de cualquier "proceso". Entrega al menos una vez: un mensaje
 * sale de la cola solo cuando el consumidor lo confirma.
 */
public final class Broker {

    private final Map<String, Deque<Mensaje>> colas = new LinkedHashMap<>();
    public final List<String> registro = new ArrayList<>();
    public final List<Mensaje> enviados = new ArrayList<>();

    public void enviar(String destino, Mensaje mensaje) {
        colas.computeIfAbsent(destino, d -> new ArrayDeque<>()).addLast(mensaje);
        enviados.add(mensaje);
        registro.add("→ " + destino + " " + mensaje.tipo() + " [" + mensaje.sagaId() + "]");
    }

    public Mensaje siguiente(String destino) {
        Deque<Mensaje> cola = colas.get(destino);
        return cola == null ? null : cola.peekFirst();
    }

    public void confirmar(String destino) {
        colas.get(destino).pollFirst();
    }

    /** Para el paso 6: una orden que nunca llega a su destino. */
    public void perderSiguiente(String destino) {
        colas.get(destino).pollFirst();
    }

    public int pendientes(String destino) {
        Deque<Mensaje> cola = colas.get(destino);
        return cola == null ? 0 : cola.size();
    }
}
