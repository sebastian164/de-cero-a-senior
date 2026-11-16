package soporte;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Lee los bloques de código de los README del curso, para ejecutarlos tal cual están escritos. */
public final class Readme {

    /** Raíz del repositorio del curso (se busca hacia arriba desde la carpeta de la solución). */
    public static final Path CURSO = encontrarRaiz();

    private Readme() {
    }

    private static Path encontrarRaiz() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("java/nivel-08-bases-de-datos/README.md"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("No se encontró la raíz del curso: ejecuta esta solución dentro del repositorio del curso, porque una prueba lee el README de la sesión para ejecutar su código tal cual");
        }
        return p;
    }

    /** Devuelve los bloques ```lenguaje del README de la sesión, en orden. */
    public static List<String> bloques(String sesion, String lenguaje) {
        try {
            List<String> lineas = Files.readAllLines(CURSO.resolve(sesion).resolve("README.md"));
            List<String> r = new ArrayList<>();
            StringBuilder actual = null;
            for (String l : lineas) {
                if (actual == null && l.strip().equals("```" + lenguaje)) {
                    actual = new StringBuilder();
                } else if (actual != null && l.strip().equals("```")) {
                    r.add(actual.toString());
                    actual = null;
                } else if (actual != null) {
                    actual.append(l).append('\n');
                }
            }
            return r;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String texto(String sesion) {
        try {
            return Files.readString(CURSO.resolve(sesion).resolve("README.md"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
