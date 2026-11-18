package notificaciones;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Cola en memoria con entrega "al menos una vez": recibir() entrega un mensaje con un arrendamiento; si no llega
 * ack() antes del vencimiento, el mensaje vuelve a la cola (como la visibilidad de SQS). Dos niveles de prioridad:
 * siempre se despacha antes lo ALTO.
 */
public class ColaConArrendamiento {
    public record Arrendamiento(long token, Notificacion notificacion, int intento) { }
    private record EnVuelo(Notificacion n, int intento, long venceNanos) { }
    private record Pendiente(Notificacion n, int intento, long disponibleNanos) { }

    private final ReentrantLock cerrojo = new ReentrantLock();
    private final Condition hayTrabajo = cerrojo.newCondition();
    private final ArrayDeque<Pendiente> alta = new ArrayDeque<>(), normal = new ArrayDeque<>();
    private final java.util.PriorityQueue<Pendiente> retrasados = new java.util.PriorityQueue<>(java.util.Comparator.comparingLong(Pendiente::disponibleNanos));
    private final Map<Long, EnVuelo> enVuelo = new HashMap<>();
    private final long arrendamientoNanos;
    private long siguienteToken = 1;
    private boolean cerrada;

    public ColaConArrendamiento(long arrendamientoMs) { this.arrendamientoNanos = arrendamientoMs * 1_000_000; }

    public void publicar(Notificacion n) { agregar(new Pendiente(n, 1, 0)); }

    /** Reintento con retraso (retroceso exponencial calculado por quien llama). */
    public void reintentar(Notificacion n, int intento, long retrasoMs) {
        agregar(new Pendiente(n, intento, System.nanoTime() + retrasoMs * 1_000_000));
    }

    private void agregar(Pendiente p) {
        cerrojo.lock();
        try {
            if (p.disponibleNanos() > 0) retrasados.add(p); else cola(p).add(p);
            hayTrabajo.signal();
        } finally { cerrojo.unlock(); }
    }

    private ArrayDeque<Pendiente> cola(Pendiente p) { return p.n().prioridad() == Notificacion.Prioridad.ALTA ? alta : normal; }

    /** Devuelve null si la cola se cerró y está vacía. */
    public Arrendamiento recibir() throws InterruptedException {
        cerrojo.lock();
        try {
            while (true) {
                long ahora = System.nanoTime();
                promover(ahora);
                Pendiente p = !alta.isEmpty() ? alta.poll() : normal.poll();
                if (p != null) {
                    long token = siguienteToken++;
                    enVuelo.put(token, new EnVuelo(p.n(), p.intento(), ahora + arrendamientoNanos));
                    return new Arrendamiento(token, p.n(), p.intento());
                }
                if (cerrada && retrasados.isEmpty() && enVuelo.isEmpty()) return null;
                hayTrabajo.awaitNanos(5_000_000);
            }
        } finally { cerrojo.unlock(); }
    }

    private void promover(long ahora) {
        while (!retrasados.isEmpty() && retrasados.peek().disponibleNanos() <= ahora) {
            Pendiente p = retrasados.poll();
            cola(p).add(p);
        }
        for (Iterator<Map.Entry<Long, EnVuelo>> it = enVuelo.entrySet().iterator(); it.hasNext(); ) {
            EnVuelo e = it.next().getValue();
            if (e.venceNanos() <= ahora) { // el trabajador no confirmó: el mensaje vuelve (se reenviará: duplicado posible)
                it.remove();
                Pendiente p = new Pendiente(e.n(), e.intento(), 0);
                cola(p).addFirst(p);
            }
        }
    }

    public void ack(long token) {
        cerrojo.lock();
        try { enVuelo.remove(token); } finally { cerrojo.unlock(); }
    }

    public void cerrar() {
        cerrojo.lock();
        try { cerrada = true; hayTrabajo.signalAll(); } finally { cerrojo.unlock(); }
    }

    public int pendientes() {
        cerrojo.lock();
        try { return alta.size() + normal.size() + retrasados.size() + enVuelo.size(); } finally { cerrojo.unlock(); }
    }
}
