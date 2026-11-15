import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/** Dos juegos de pruebas frente a un refactor que no cambia el comportamiento. */
public class Main {

    // ----- Puertos: lo que el caso de uso necesita del mundo -----
    interface Prestamos {
        int activosDe(String socio);
        List<String> librosDe(String socio);
        void registrar(String socio, String libro, LocalDate vence);
    }

    interface Reloj {
        LocalDate hoy();
    }

    interface PrestarLibro {
        String ejecutar(String socio, String libro);
    }

    // ----- El caso de uso, antes y después de un refactor interno -----
    static PrestarLibro version1(Prestamos prestamos, Reloj reloj) {
        return (socio, libro) -> {
            if (prestamos.activosDe(socio) >= 3) {
                return "RECHAZADO";
            }
            prestamos.registrar(socio, libro, reloj.hoy().plusDays(14));
            return "PRESTADO";
        };
    }

    static PrestarLibro version2(Prestamos prestamos, Reloj reloj) {
        return (socio, libro) -> {
            if (prestamos.librosDe(socio).size() >= 3) {      // misma regla, consultada de otra forma
                return "RECHAZADO";
            }
            prestamos.registrar(socio, libro, reloj.hoy().plusDays(14));
            return "PRESTADO";
        };
    }

    // ----- Dobles -----
    /** Falso: un repositorio de verdad, en memoria. */
    static final class PrestamosEnMemoria implements Prestamos {
        final Map<String, Map<String, LocalDate>> porSocio = new LinkedHashMap<>();

        public int activosDe(String socio) {
            return librosDe(socio).size();
        }

        public List<String> librosDe(String socio) {
            return new ArrayList<>(porSocio.getOrDefault(socio, Map.of()).keySet());
        }

        public void registrar(String socio, String libro, LocalDate vence) {
            porSocio.computeIfAbsent(socio, s -> new LinkedHashMap<>()).put(libro, vence);
        }
    }

    /** Espía que anota cada llamada: la herramienta de la prueba atada a la implementación. */
    static final class PrestamosEspia implements Prestamos {
        final List<String> llamadas = new ArrayList<>();

        public int activosDe(String socio) {
            llamadas.add("activosDe");
            return 0;
        }

        public List<String> librosDe(String socio) {
            llamadas.add("librosDe");
            return List.of();
        }

        public void registrar(String socio, String libro, LocalDate vence) {
            llamadas.add("registrar");
        }
    }

    static final Reloj QUINCE_DE_NOVIEMBRE = () -> LocalDate.of(2026, 11, 15);

    // ----- Antes: una prueba que comprueba CÓMO trabaja el caso de uso -----
    static void pruebaAtadaALaImplementacion(BiFunction<Prestamos, Reloj, PrestarLibro> fabrica) {
        PrestamosEspia espia = new PrestamosEspia();
        fabrica.apply(espia, QUINCE_DE_NOVIEMBRE).ejecutar("ana", "Rayuela");
        comprobar("llama a activosDe y después a registrar", List.of("activosDe", "registrar"), espia.llamadas);
    }

    // ----- Después: pruebas que comprueban QUÉ logra -----
    static void pruebasDeComportamiento(BiFunction<Prestamos, Reloj, PrestarLibro> fabrica) {
        PrestamosEnMemoria prestamos = new PrestamosEnMemoria();
        PrestarLibro prestar = fabrica.apply(prestamos, QUINCE_DE_NOVIEMBRE);

        comprobar("un socio sin préstamos recibe el libro", "PRESTADO", prestar.ejecutar("ana", "Rayuela"));
        comprobar("el préstamo vence a los catorce días",
                LocalDate.of(2026, 11, 29), prestamos.porSocio.get("ana").get("Rayuela"));

        prestar.ejecutar("ana", "Ficciones");
        prestar.ejecutar("ana", "Pedro Páramo");
        comprobar("con tres préstamos activos se rechaza el cuarto", "RECHAZADO", prestar.ejecutar("ana", "El túnel"));
        comprobar("el rechazo no registra nada", 3, prestamos.activosDe("ana"));
        comprobar("el límite es por socio", "PRESTADO", prestar.ejecutar("luis", "El túnel"));
    }

    public static void main(String[] args) {
        System.out.println("Versión 1, prueba atada a la implementación:");
        pruebaAtadaALaImplementacion(Main::version1);
        System.out.println("Versión 1, pruebas de comportamiento:");
        pruebasDeComportamiento(Main::version1);

        System.out.println();
        System.out.println("Se refactoriza. El comportamiento es el mismo.");
        System.out.println();

        System.out.println("Versión 2, prueba atada a la implementación:");
        pruebaAtadaALaImplementacion(Main::version2);
        System.out.println("Versión 2, pruebas de comportamiento:");
        pruebasDeComportamiento(Main::version2);
    }

    static void comprobar(String nombre, Object esperado, Object obtenido) {
        if (Objects.equals(esperado, obtenido)) {
            System.out.println("  ok    " + nombre);
        } else {
            System.out.println("  FALLA " + nombre + ": esperaba " + esperado + " y obtuve " + obtenido);
        }
    }
}
