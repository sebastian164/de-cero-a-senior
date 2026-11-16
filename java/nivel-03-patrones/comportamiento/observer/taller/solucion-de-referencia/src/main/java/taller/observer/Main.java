package taller.observer;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class Main {

    public static void main(String[] args) {
        List<String> salida = new ArrayList<>();
        Biblioteca b = new Biblioteca(salida::add);
        b.prestar("Cien años", "ana", LocalDate.of(2025, 5, 1));
        b.ponerEnEspera("Cien años", "luis");
        b.suscribir(new AvisoListaEspera(salida::add));
        b.suscribir(new CalculoMulta(salida::add));
        b.suscribir(new Estadisticas());
        b.suscribir(e -> { throw new IllegalStateException("analítica caída"); });
        b.devolver("Cien años", LocalDate.of(2025, 5, 20));
        salida.forEach(System.out::println);
        System.out.println("Disponible: " + b.disponible("Cien años"));

        System.out.println("Versión directa: " + BibliotecaDirecta.demostrar());
        System.out.println("Fuga sin desuscribir: " + Fuga.demostrar());
    }
}

/** El evento lleva el resultado de la operación, incluido a quién le tocó el libro en la lista de espera. */
record Devolucion(String libro, String usuario, LocalDate prestado, LocalDate devuelto, Optional<String> siguienteEnEspera) {
    long diasDeRetraso(int diasPermitidos) {
        return Math.max(0, ChronoUnit.DAYS.between(prestado, devuelto) - diasPermitidos);
    }
}

interface OyenteDevolucion {
    void alDevolver(Devolucion d);
}

/**
 * El sujeto. La disponibilidad y el avance de la lista de espera son parte de la operación, no
 * oyentes (paso 2): si fallan, la devolución falla. Avisar es la reacción.
 */
final class Biblioteca {
    private final Map<String, String> prestamos = new HashMap<>();
    private final Map<String, LocalDate> fechas = new HashMap<>();
    private final Map<String, Deque<String>> espera = new HashMap<>();
    private final Set<String> disponibles = new HashSet<>();
    private final List<OyenteDevolucion> oyentes = new CopyOnWriteArrayList<>();
    private final Consumer<String> errores;

    Biblioteca(Consumer<String> errores) { this.errores = errores; }

    void prestar(String libro, String usuario, LocalDate fecha) {
        prestamos.put(libro, usuario);
        fechas.put(libro, fecha);
        disponibles.remove(libro);
    }

    void ponerEnEspera(String libro, String usuario) { espera.computeIfAbsent(libro, k -> new ArrayDeque<>()).add(usuario); }
    List<String> enEspera(String libro) { return List.copyOf(espera.getOrDefault(libro, new ArrayDeque<>())); }

    void suscribir(OyenteDevolucion o) { oyentes.add(o); }
    void desuscribir(OyenteDevolucion o) { oyentes.remove(o); }
    int cantidadOyentes() { return oyentes.size(); }

    void devolver(String libro, LocalDate hoy) {
        String usuario = prestamos.remove(libro);
        if (usuario == null) throw new IllegalStateException(libro + " no está prestado");
        disponibles.add(libro);                          // parte de la operación: si falla, la devolución falla
        Deque<String> cola = espera.get(libro);
        Optional<String> siguiente = Optional.ofNullable(cola == null ? null : cola.poll());   // también parte de la operación
        Devolucion d = new Devolucion(libro, usuario, fechas.remove(libro), hoy, siguiente);
        for (OyenteDevolucion o : oyentes) {
            try {
                o.alDevolver(d);
            } catch (RuntimeException e) {
                // Paso 3: un oyente que falla no tumba a los demás ni deshace la devolución.
                errores.accept("ERROR en oyente " + o.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }

    boolean disponible(String libro) { return disponibles.contains(libro); }
}

/** Solo reacciona: lee del evento a quién avisar; no conoce a la Biblioteca ni toca su estado. */
final class AvisoListaEspera implements OyenteDevolucion {
    private final Consumer<String> correo;
    AvisoListaEspera(Consumer<String> correo) { this.correo = correo; }
    @Override public void alDevolver(Devolucion d) {
        d.siguienteEnEspera().ifPresent(u -> correo.accept("Aviso a " + u + ": '" + d.libro() + "' ya está disponible"));
    }
}

final class CalculoMulta implements OyenteDevolucion {
    static final int DIAS_PERMITIDOS = 15;
    static final long MULTA_DIARIA = 1_000;
    private final Consumer<String> salida;
    CalculoMulta(Consumer<String> salida) { this.salida = salida; }
    @Override public void alDevolver(Devolucion d) {
        long dias = d.diasDeRetraso(DIAS_PERMITIDOS);
        if (dias > 0) salida.accept("Multa a " + d.usuario() + ": $" + dias * MULTA_DIARIA + " por " + dias + " días");
    }
}

final class Estadisticas implements OyenteDevolucion {
    final Map<String, Integer> lecturasPorLibro = new HashMap<>();
    @Override public void alDevolver(Devolucion d) { lecturasPorLibro.merge(d.libro(), 1, Integer::sum); }
}

/**
 * Paso 1: la versión con llamadas directas. devolver() conoce y llama a cada consecuencia;
 * agregar una nueva es modificar este método, y si una falla, las siguientes no se ejecutan.
 */
final class BibliotecaDirecta {
    private final Map<String, String> prestamos = new HashMap<>();
    private final Map<String, LocalDate> fechas = new HashMap<>();
    private final Map<String, Deque<String>> espera = new HashMap<>();
    private final Set<String> disponibles = new HashSet<>();
    private final Map<String, Integer> lecturas = new HashMap<>();
    private final List<String> correos = new ArrayList<>();
    private final List<String> multas = new ArrayList<>();

    void prestar(String libro, String usuario, LocalDate fecha) { prestamos.put(libro, usuario); fechas.put(libro, fecha); }
    void ponerEnEspera(String libro, String usuario) { espera.computeIfAbsent(libro, k -> new ArrayDeque<>()).add(usuario); }

    void devolver(String libro, LocalDate hoy) {
        String usuario = prestamos.remove(libro);
        LocalDate prestado = fechas.remove(libro);
        disponibles.add(libro);
        Deque<String> q = espera.get(libro);
        String siguiente = q == null ? null : q.poll();
        if (siguiente != null) correos.add("Aviso a " + siguiente + ": '" + libro + "' ya está disponible");
        long dias = Math.max(0, ChronoUnit.DAYS.between(prestado, hoy) - CalculoMulta.DIAS_PERMITIDOS);
        if (dias > 0) multas.add("Multa a " + usuario + ": $" + dias * CalculoMulta.MULTA_DIARIA + " por " + dias + " días");
        lecturas.merge(libro, 1, Integer::sum);
    }

    static String demostrar() {
        BibliotecaDirecta b = new BibliotecaDirecta();
        b.prestar("L", "ana", LocalDate.of(2025, 1, 1));
        b.ponerEnEspera("L", "luis");
        b.devolver("L", LocalDate.of(2025, 1, 20));
        return "disponible=" + b.disponibles.contains("L") + " " + b.correos + " " + b.multas + " lecturas=" + b.lecturas;
    }
}

/** Paso 4: una vista de corta vida que se suscribe y nunca se desuscribe. */
final class Fuga {
    static final class PanelPrestamos implements OyenteDevolucion {
        final byte[] datos = new byte[1024 * 1024];      // 1 MB de estado de pantalla
        @Override public void alDevolver(Devolucion d) { }
    }

    /** Devuelve [oyentes tras abrir 50 paneles sin desuscribir, oyentes desuscribiéndolos]. */
    static String demostrar() {
        Biblioteca b = new Biblioteca(s -> { });
        for (int i = 0; i < 50; i++) {
            PanelPrestamos panel = new PanelPrestamos();   // se "cierra" al terminar la iteración...
            b.suscribir(panel);                            // ...pero la biblioteca lo sigue referenciando
        }
        int sinDesuscribir = b.cantidadOyentes();
        Biblioteca c = new Biblioteca(s -> { });
        for (int i = 0; i < 50; i++) {
            PanelPrestamos panel = new PanelPrestamos();
            c.suscribir(panel);
            c.desuscribir(panel);                          // al cerrar la vista
        }
        return sinDesuscribir + " paneles (~" + sinDesuscribir + " MB) retenidos; desuscribiendo: " + c.cantidadOyentes();
    }
}
