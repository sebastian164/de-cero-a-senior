package banco;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Lee los import de cada archivo y comprueba que las dependencias apuntan hacia dentro. */
public final class PruebasDeArquitectura {

    private PruebasDeArquitectura() {
    }

    public static void ejecutar() {
        System.out.println("Arquitectura:");
        Path fuentes = Path.of(System.getProperty("fuentes", "src"));
        if (!Files.isDirectory(fuentes.resolve("banco"))) {
            Comprobar.igual("se encuentran las fuentes (ejecuta desde proyecto/ o indica -Dfuentes=ruta)", true, false);
            return;
        }
        Comprobar.igual("el dominio solo depende de sí mismo y del JDK", List.of(),
                importsQue(fuentes, "banco/dominio", fuera("java.", "banco.dominio.")));
        Comprobar.igual("la aplicación solo depende del dominio, de sí misma y del JDK", List.of(),
                importsQue(fuentes, "banco/aplicacion", fuera("java.", "banco.dominio.", "banco.aplicacion.")));
        Comprobar.igual("los adaptadores de entrada no conocen a los de salida", List.of(),
                importsQue(fuentes, "banco/adaptadores/entrada", dentro("banco.adaptadores.salida.")));
        Comprobar.igual("los adaptadores de salida no conocen a los de entrada", List.of(),
                importsQue(fuentes, "banco/adaptadores/salida", dentro("banco.adaptadores.entrada.")));
        Comprobar.igual("los adaptadores de entrada solo usan puertos de entrada", List.of(),
                importsQue(fuentes, "banco/adaptadores/entrada", dentro("banco.aplicacion.puertos.salida.", "banco.aplicacion.ServicioDeCuentas")));
    }

    private static Predicate<String> dentro(String... prefijos) {
        return importado -> Arrays.stream(prefijos).anyMatch(importado::startsWith);
    }

    private static Predicate<String> fuera(String... prefijos) {
        return dentro(prefijos).negate();
    }

    /** Los import de los archivos de una carpeta que cumplen la condición, como "Archivo.java -> lo.importado". */
    private static List<String> importsQue(Path fuentes, String carpeta, Predicate<String> prohibido) {
        List<String> encontrados = new ArrayList<>();
        try (Stream<Path> archivos = Files.walk(fuentes.resolve(carpeta))) {
            for (Path archivo : archivos.filter(ruta -> ruta.toString().endsWith(".java")).sorted().toList()) {
                for (String linea : Files.readAllLines(archivo)) {
                    if (linea.startsWith("import ")) {
                        String importado = linea.substring("import ".length()).replace("static ", "").replace(";", "").trim();
                        if (prohibido.test(importado)) {
                            encontrados.add(archivo.getFileName() + " -> " + importado);
                        }
                    }
                }
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        return encontrados;
    }
}
