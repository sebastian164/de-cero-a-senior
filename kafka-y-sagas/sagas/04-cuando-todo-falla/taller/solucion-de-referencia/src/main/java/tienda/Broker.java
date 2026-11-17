package tienda;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * El broker hostil (paso 1). Envuelve las colas por destino y aplica un guion determinista: al n-ésimo mensaje
 * enviado le hace lo que diga el guion. Sin azar: el mismo guion da siempre el mismo resultado.
 */
public final class Broker {

    public enum Accion { DUPLICAR, INTERCAMBIAR, RETRASAR, PERDER }

    private record Retenido(String destino, Mensaje mensaje, long liberarEnCiclo) {
    }

    private final Map<String, Deque<Mensaje>> colas = new LinkedHashMap<>();
    private final Map<Integer, Accion> guion = new HashMap<>();
    private final Map<String, Mensaje> esperandoIntercambio = new HashMap<>();
    private final List<Retenido> retenidos = new ArrayList<>();
    public final List<String> registro = new ArrayList<>();
    public final List<Mensaje> enviados = new ArrayList<>();
    public int retraso = 5;                 // ciclos que se retiene un mensaje RETRASAR
    private int numero;
    private long ciclo;

    /** El n-ésimo mensaje (empezando en 1) sufrirá la acción. */
    public Broker con(int n, Accion accion) {
        guion.put(n, accion);
        return this;
    }

    public void enviar(String destino, Mensaje mensaje) {
        enviados.add(mensaje);
        Accion accion = guion.get(++numero);
        if (accion == null) {
            poner(destino, mensaje);
            return;
        }
        registro.add(accion + " #" + numero + " " + mensaje.tipo() + " [" + mensaje.sagaId() + "] → " + destino);
        switch (accion) {
            case DUPLICAR -> {
                poner(destino, mensaje);
                poner(destino, mensaje);
            }
            case PERDER -> { }
            case RETRASAR -> retenidos.add(new Retenido(destino, mensaje, ciclo + retraso));
            case INTERCAMBIAR -> esperandoIntercambio.put(destino, mensaje);   // sale detrás del siguiente
        }
    }

    private void poner(String destino, Mensaje mensaje) {
        colas.computeIfAbsent(destino, d -> new ArrayDeque<>()).addLast(mensaje);
        Mensaje detras = esperandoIntercambio.remove(destino);
        if (detras != null) {
            colas.get(destino).addLast(detras);
        }
    }

    /** Avanza el reloj del broker: libera retrasados y, si nadie más vino, el que esperaba para intercambiarse. */
    public void tic() {
        ciclo++;
        for (var it = retenidos.iterator(); it.hasNext(); ) {
            Retenido r = it.next();
            if (r.liberarEnCiclo() <= ciclo) {
                it.remove();
                colas.computeIfAbsent(r.destino(), d -> new ArrayDeque<>()).addLast(r.mensaje());
            }
        }
        if (ciclo % 10 == 0) {
            esperandoIntercambio.forEach((d, m) -> colas.computeIfAbsent(d, x -> new ArrayDeque<>()).addLast(m));
            esperandoIntercambio.clear();
        }
    }

    public Mensaje siguiente(String destino) {
        Deque<Mensaje> cola = colas.get(destino);
        return cola == null ? null : cola.peekFirst();
    }

    public void confirmar(String destino) {
        colas.get(destino).pollFirst();
    }

    public boolean vacio() {
        return retenidos.isEmpty() && esperandoIntercambio.isEmpty() && colas.values().stream().allMatch(Deque::isEmpty);
    }
}
