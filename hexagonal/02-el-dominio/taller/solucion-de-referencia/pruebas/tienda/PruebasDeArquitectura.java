package tienda;

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
        if (!Files.isDirectory(fuentes.resolve("tienda"))) {
            Comprobar.igual("se encuentran las fuentes (indica -Dfuentes=ruta)", true, false);
            return;
        }
        Comprobar.igual("el dominio solo depende de sí mismo y de java.* sin entrada y salida", List.of(),
                importsQue(fuentes, "tienda/dominio", fuera("java.lang.", "java.time.", "java.util.", "tienda.dominio.")));
        if (Files.isDirectory(fuentes.resolve("tienda/aplicacion"))) {
            Comprobar.igual("la aplicación solo depende del dominio, de sí misma y del JDK sin entrada y salida", List.of(),
                    importsQue(fuentes, "tienda/aplicacion", fuera("java.lang.", "java.time.", "java.util.", "tienda.dominio.", "tienda.aplicacion.")));
        }
        if (Files.isDirectory(fuentes.resolve("tienda/adaptadores/entrada"))) {
            Comprobar.igual("los adaptadores de entrada no conocen a los de salida", List.of(),
                    importsQue(fuentes, "tienda/adaptadores/entrada", dentro("tienda.adaptadores.salida.")));
            Comprobar.igual("los adaptadores de entrada solo usan puertos de entrada", List.of(),
                    importsQue(fuentes, "tienda/adaptadores/entrada", dentro("tienda.aplicacion.puertos.salida.", "tienda.aplicacion.ServicioDePedidos")));
        }
        if (Files.isDirectory(fuentes.resolve("tienda/adaptadores/salida"))) {
            Comprobar.igual("los adaptadores de salida no conocen a los de entrada", List.of(),
                    importsQue(fuentes, "tienda/adaptadores/salida", dentro("tienda.adaptadores.entrada.")));
        }
    }

    private static Predicate<String> dentro(String... prefijos) {
        return importado -> Arrays.stream(prefijos).anyMatch(importado::startsWith);
    }

    private static Predicate<String> fuera(String... prefijos) {
        return dentro(prefijos).negate();
    }

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
