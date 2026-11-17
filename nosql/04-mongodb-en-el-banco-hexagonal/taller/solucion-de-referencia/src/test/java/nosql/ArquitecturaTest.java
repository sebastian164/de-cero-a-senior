package nosql;

import static org.junit.jupiter.api.Assertions.*;

import comun.Arquitectura;
import comun.Salida;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pasos 4 y 8 y el criterio "el dominio y la aplicación no cambiaron". */
class ArquitecturaTest {
    static final Path NUCLEO = Path.of("../../../../hexagonal/08-pruebas-y-limites/taller/solucion-de-referencia/src");

    @Test
    void comMongodbSoloEnElAdaptador() {
        List<Path> fuentes = new ArrayList<>(Arquitectura.fuentes(Path.of("src/main/java")));
        fuentes.addAll(Arquitectura.fuentes(NUCLEO));
        assertEquals(List.of(), Arquitectura.violaciones(fuentes));
        assertTrue(Arquitectura.conTecnologiaEnAdaptadores(fuentes) >= 6, "la prueba mira de verdad los adaptadores de MongoDB");
    }

    /** El núcleo se compila desde donde está: este módulo no declara ninguna clase en los paquetes de su dominio ni de su aplicación. */
    @Test
    void elModuloNoTocaElDominioNiLaAplicacionDelNucleo() throws IOException {
        for (Path p : Arquitectura.fuentes(Path.of("src/main/java"))) {
            String paquete = Files.readAllLines(p).stream().filter(l -> l.startsWith("package ")).findFirst().orElse("");
            assertFalse(paquete.equals("package tienda.dominio;") || paquete.equals("package tienda.aplicacion;") || paquete.startsWith("package tienda.aplicacion.puertos"),
                    p + " declara " + paquete + ": eso es tocar el núcleo");
        }
    }

    /** La única variación de la aplicación (paso 7): el servicio con espera es una copia con una sola diferencia, la política de reintento. */
    @Test
    void laUnicaDiferenciaEsLaPoliticaDeReintento() throws Exception {
        Path original = NUCLEO.resolve("tienda/aplicacion/ServicioDePedidos.java");
        Path copia = Path.of("src/main/java/nosql/aplicacion/ServicioDePedidosConEspera.java");
        Process p = new ProcessBuilder("diff", "-u", original.toString(), copia.toString()).redirectErrorStream(true).start();
        String diff = new String(p.getInputStream().readAllBytes());
        p.waitFor();
        Salida.limpiar("diff-reintento.txt");
        Salida.linea("diff-reintento.txt", diff);
        long cambiadas = diff.lines().filter(l -> (l.startsWith("+") || l.startsWith("-")) && !l.startsWith("+++") && !l.startsWith("---")).count();
        assertTrue(cambiadas > 0 && cambiadas < 45, "pocas líneas cambiadas: " + cambiadas);
        for (String l : diff.lines().filter(l -> l.startsWith("+") || l.startsWith("-")).toList()) {
            if (l.startsWith("+++") || l.startsWith("---") || l.isBlank()) continue;
            assertTrue(l.matches("(?i).*(polític|politica|intento|import |package |ServicioDePedidos|conflicto|INTENTOS|\\{|\\}|/\\*\\*|\\*/|\\*|esperar|Si otro).*"),
                    "una línea que no es de la política de reintento: " + l);
        }
    }
}
