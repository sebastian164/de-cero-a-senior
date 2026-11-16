package patrones.saga;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Saga orquestada: ejecuta en orden y, si un paso falla, compensa los anteriores en orden inverso. */
public class Orquestador<C> {

    public record Resultado(boolean exito, List<String> bitacora) { }

    private final List<Paso<C>> pasos;

    public Orquestador(List<Paso<C>> pasos) { this.pasos = List.copyOf(pasos); }

    public Resultado ejecutar(C contexto) {
        List<String> bitacora = new ArrayList<>();
        Deque<Paso<C>> hechos = new ArrayDeque<>();
        for (Paso<C> paso : pasos) {
            try {
                paso.ejecutar(contexto);
                hechos.push(paso);
                bitacora.add("OK " + paso.nombre());
            } catch (RuntimeException e) {
                bitacora.add("FALLO " + paso.nombre() + ": " + e.getMessage());
                while (!hechos.isEmpty()) {
                    Paso<C> aDeshacer = hechos.pop();
                    compensarConReintentos(aDeshacer, contexto, bitacora);
                }
                return new Resultado(false, bitacora);
            }
        }
        return new Resultado(true, bitacora);
    }

    private void compensarConReintentos(Paso<C> paso, C contexto, List<String> bitacora) {
        for (int intento = 1; intento <= 3; intento++) {
            try {
                paso.compensar(contexto);
                bitacora.add("COMPENSADO " + paso.nombre());
                return;
            } catch (RuntimeException e) {
                bitacora.add("REINTENTO compensación " + paso.nombre() + " (" + intento + ")");
            }
        }
        bitacora.add("PENDIENTE MANUAL " + paso.nombre());
    }
}
