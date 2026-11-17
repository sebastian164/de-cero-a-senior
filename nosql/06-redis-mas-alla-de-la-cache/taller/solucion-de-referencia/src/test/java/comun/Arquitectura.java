package comun;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * La prueba de arquitectura de las sesiones 04 a 07: las tecnologías (MongoDB, Redis, el servidor HTTP) solo se importan en
 * los adaptadores (carpeta {@code adaptadores}) y en la raíz de composición. El dominio y la aplicación no las conocen.
 */
public final class Arquitectura {
    private Arquitectura() {}

    public static final List<String> PROHIBIDOS = List.of("com.mongodb", "org.bson", "io.lettuce", "com.sun.net.httpserver");
    /** Las raíces de composición: cablean adaptadores, por eso pueden importarlos. */
    public static final List<String> RAICES_DE_COMPOSICION = List.of("Composicion.java", "Servidor.java");

    public static List<Path> fuentes(Path raiz) {
        try (Stream<Path> s = Files.walk(raiz)) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    /** Las violaciones, una por fichero e importación: "ruta importa X" fuera de un adaptador o de la raíz de composición. */
    public static List<String> violaciones(List<Path> fuentes) {
        List<String> v = new ArrayList<>();
        for (Path p : fuentes) {
            String ruta = p.toString().replace('\\', '/');
            boolean permitido = ruta.contains("/adaptadores/") || RAICES_DE_COMPOSICION.contains(p.getFileName().toString());
            if (permitido) continue;
            try {
                for (String linea : Files.readAllLines(p)) {
                    String t = linea.trim();
                    if (!t.startsWith("import ")) continue;
                    for (String prohibido : PROHIBIDOS) if (t.contains(" " + prohibido) || t.contains(" static " + prohibido)) v.add(ruta + " importa " + prohibido);
                }
            } catch (IOException e) { throw new RuntimeException(e); }
        }
        return v;
    }

    /** Cuántos ficheros sí importan tecnología dentro de adaptadores (para que la prueba no pase por no mirar nada). */
    public static long conTecnologiaEnAdaptadores(List<Path> fuentes) {
        return fuentes.stream().filter(p -> p.toString().contains("/adaptadores/")).filter(p -> {
            try { return Files.readString(p).lines().anyMatch(l -> l.startsWith("import ") && PROHIBIDOS.stream().anyMatch(l::contains)); }
            catch (IOException e) { throw new RuntimeException(e); }
        }).count();
    }
}
