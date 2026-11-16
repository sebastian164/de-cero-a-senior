package taller.mediator.sinmediador;

import java.util.ArrayList;
import java.util.List;

/**
 * Paso 1: sin mediador. Cada avión guarda la lista de TODOS los demás y les pregunta
 * si están usando la pista. Con n aviones hay n*(n-1) referencias, y la regla de
 * prioridad no tiene dónde vivir: quedaría repartida en cada avión.
 */
public final class Aeropuerto {

    static final class Avion {
        final String matricula;
        final List<Avion> otros = new ArrayList<>();
        boolean enPista;
        Avion(String matricula) { this.matricula = matricula; }

        void conocer(Avion otro) { otros.add(otro); }

        boolean intentarUsarPista() {
            for (Avion o : otros) {
                if (o.enPista) return false;      // cada avión conoce el estado interno de los otros
            }
            enPista = true;
            return true;
        }
    }

    public static int referencias(int aviones) {
        List<Avion> todos = crear(aviones);
        return todos.stream().mapToInt(a -> a.otros.size()).sum();
    }

    static List<Avion> crear(int n) {
        List<Avion> todos = new ArrayList<>();
        for (int i = 0; i < n; i++) todos.add(new Avion("A" + i));
        for (Avion a : todos) for (Avion b : todos) if (a != b) a.conocer(b);
        return todos;
    }

    public static List<String> demostrar() {
        List<Avion> todos = crear(3);
        List<String> r = new ArrayList<>();
        r.add("Sin mediador, A0 usa pista: " + todos.get(0).intentarUsarPista());
        r.add("Sin mediador, A1 usa pista: " + todos.get(1).intentarUsarPista());
        r.add("Referencias entre 3 aviones: " + referencias(3) + "; entre 10: " + referencias(10));
        return r;
    }

    private Aeropuerto() { }
}
