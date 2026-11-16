package taller.memento;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        Configurador pc = new Configurador();
        Historial h = new Historial(pc, 20);
        h.cambiar(() -> pc.procesador("Ryzen 7"));
        h.cambiar(() -> pc.memoria(32));
        h.cambiar(() -> pc.agregarAccesorio("Teclado"));
        h.cambiar(() -> pc.agregarAccesorio("Mouse"));
        System.out.println("Actual:   " + pc);
        h.deshacer();
        h.deshacer();
        System.out.println("Deshacer x2: " + pc);
        h.rehacer();
        System.out.println("Rehacer:  " + pc);

        HistorialInmutable hi = new HistorialInmutable(Configuracion.BASE, 20);
        hi.aplicar(c -> c.conProcesador("Ryzen 7"));
        hi.aplicar(c -> c.conAccesorio("Teclado"));
        hi.deshacer();
        System.out.println("Inmutable tras deshacer: " + hi.actual());
    }
}

// ===================== Versión Memento =====================

/** Originador: su estado es privado; solo él sabe crear y leer sus mementos. */
final class Configurador {
    private String procesador = "Core i3";
    private int memoriaGb = 8;
    private String disco = "SSD 256";
    private final List<String> accesorios = new ArrayList<>();

    void procesador(String p) { this.procesador = p; }
    void memoria(int gb) { this.memoriaGb = gb; }
    void disco(String d) { this.disco = d; }
    void agregarAccesorio(String a) { accesorios.add(a); }

    /** El memento es opaco para el cuidador: no expone nada. Copia la lista (paso 1). */
    Memento guardar() { return new Instantanea(procesador, memoriaGb, disco, List.copyOf(accesorios)); }

    void restaurar(Memento m) {
        Instantanea s = (Instantanea) m;
        procesador = s.procesador;
        memoriaGb = s.memoriaGb;
        disco = s.disco;
        accesorios.clear();
        accesorios.addAll(s.accesorios);
    }

    interface Memento { }

    private record Instantanea(String procesador, int memoriaGb, String disco, List<String> accesorios) implements Memento { }

    @Override public String toString() { return procesador + ", " + memoriaGb + " GB, " + disco + ", " + accesorios; }
}

/** Cuidador con deshacer/rehacer y límite de instantáneas (paso 2). */
final class Historial {
    private final Configurador original;
    private final int limite;
    private final Deque<Configurador.Memento> atras = new ArrayDeque<>();
    private final Deque<Configurador.Memento> adelante = new ArrayDeque<>();

    Historial(Configurador original, int limite) { this.original = original; this.limite = limite; }

    void cambiar(Runnable cambio) {
        atras.push(original.guardar());
        if (atras.size() > limite) atras.removeLast();    // descarta la más vieja
        adelante.clear();
        cambio.run();
    }

    boolean deshacer() {
        if (atras.isEmpty()) return false;
        adelante.push(original.guardar());
        original.restaurar(atras.pop());
        return true;
    }

    boolean rehacer() {
        if (adelante.isEmpty()) return false;
        atras.push(original.guardar());
        original.restaurar(adelante.pop());
        return true;
    }

    int instantaneas() { return atras.size(); }
}

// ===================== Paso 3: versión inmutable =====================

record Configuracion(String procesador, int memoriaGb, String disco, List<String> accesorios) {
    static final Configuracion BASE = new Configuracion("Core i3", 8, "SSD 256", List.of());
    Configuracion { accesorios = List.copyOf(accesorios); }
    Configuracion conProcesador(String p) { return new Configuracion(p, memoriaGb, disco, accesorios); }
    Configuracion conMemoria(int gb) { return new Configuracion(procesador, gb, disco, accesorios); }
    Configuracion conAccesorio(String a) {
        List<String> nueva = new ArrayList<>(accesorios);
        nueva.add(a);
        return new Configuracion(procesador, memoriaGb, disco, nueva);
    }
}

/** Cada versión ES su propia instantánea: el historial guarda valores. */
final class HistorialInmutable {
    private final int limite;
    private final Deque<Configuracion> atras = new ArrayDeque<>();
    private final Deque<Configuracion> adelante = new ArrayDeque<>();
    private Configuracion actual;

    HistorialInmutable(Configuracion inicial, int limite) { this.actual = inicial; this.limite = limite; }

    void aplicar(java.util.function.UnaryOperator<Configuracion> cambio) {
        atras.push(actual);
        if (atras.size() > limite) atras.removeLast();
        adelante.clear();
        actual = cambio.apply(actual);
    }

    boolean deshacer() {
        if (atras.isEmpty()) return false;
        adelante.push(actual);
        actual = atras.pop();
        return true;
    }

    boolean rehacer() {
        if (adelante.isEmpty()) return false;
        atras.push(actual);
        actual = adelante.pop();
        return true;
    }

    Configuracion actual() { return actual; }
    int instantaneas() { return atras.size(); }
}
