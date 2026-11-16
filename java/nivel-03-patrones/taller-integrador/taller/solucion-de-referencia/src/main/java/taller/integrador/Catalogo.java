package taller.integrador;

import java.util.List;

/**
 * Requisito 1: cursos > módulos > lecciones. La profundidad es FIJA (tres niveles), así que no hace
 * falta Composite: records con listas. Los tipos de lección son pocos y estables y las operaciones
 * sobre ellos varias: sealed + switch.
 */
final class Catalogo {
    private Catalogo() { }

    record Curso(String id, String nombre, String categoria, long precio, List<Modulo> modulos) {
        Curso { modulos = List.copyOf(modulos); }

        List<Leccion> lecciones() { return modulos.stream().flatMap(m -> m.lecciones().stream()).toList(); }

        Leccion leccion(String id) {
            return lecciones().stream().filter(l -> l.id().equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Lección inexistente: " + id));
        }

        int minutosEstimados() { return lecciones().stream().mapToInt(Catalogo::minutos).sum(); }
    }

    record Modulo(String nombre, List<Leccion> lecciones) {
        Modulo { lecciones = List.copyOf(lecciones); }
    }

    sealed interface Leccion permits Video, Lectura, Examen, TallerEnVivo {
        String id();
        String titulo();
    }
    record Video(String id, String titulo, int minutos) implements Leccion { }
    record Lectura(String id, String titulo, int paginas) implements Leccion { }
    record Examen(String id, String titulo, List<String> preguntas) implements Leccion {
        Examen { preguntas = List.copyOf(preguntas); }
    }
    /** Parte 3, cambio 2: tipo nuevo de lección, con cupo. */
    record TallerEnVivo(String id, String titulo, int minutos, int cupo) implements Leccion { }

    /** Al agregar TallerEnVivo el compilador señaló este switch y el de describir(). */
    static int minutos(Leccion l) {
        return switch (l) {
            case Video v -> v.minutos();
            case Lectura r -> r.paginas() * 3;
            case Examen e -> e.preguntas().size() * 2;
            case TallerEnVivo t -> t.minutos();
        };
    }

    static String describir(Leccion l) {
        return switch (l) {
            case Video v -> "video '" + v.titulo() + "' (" + v.minutos() + " min)";
            case Lectura r -> "lectura '" + r.titulo() + "' (" + r.paginas() + " págs)";
            case Examen e -> "examen '" + e.titulo() + "' (" + e.preguntas().size() + " preguntas)";
            case TallerEnVivo t -> "taller en vivo '" + t.titulo() + "' (cupo " + t.cupo() + ")";
        };
    }
}
