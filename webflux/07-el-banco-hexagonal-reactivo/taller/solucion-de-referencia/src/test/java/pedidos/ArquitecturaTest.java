package pedidos;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Mira el texto completo de cada archivo, no solo los imports: un nombre totalmente calificado no se escapa. */
class ArquitecturaTest {
    static final Path APLICACION = Path.of("src/main/java/pedidos/aplicacion");
    static final Path DOMINIO = Path.of("../../../../hexagonal/08-pruebas-y-limites/taller/solucion-de-referencia/src/tienda/dominio");

    @Test void elDominioDeLaTiendaNoNombraReactorNiSpring() throws IOException {
        assertThat(DOMINIO).isDirectory();
        assertThat(texto(DOMINIO)).noneMatch(l -> l.contains("reactor.") || l.contains("org.springframework"));
    }

    @Test void laAplicacionReactivaConoceReactorPeroNoSpring() throws IOException {
        List<String> lineas = texto(APLICACION.resolve("reactiva"));
        assertThat(lineas).anyMatch(l -> l.startsWith("import reactor.core.publisher"));
        assertThat(lineas).noneMatch(l -> l.contains("org.springframework"));
    }

    @Test void laAplicacionSincronaNoConoceReactorNiSpring() throws IOException {
        assertThat(texto(APLICACION.resolve("sincrona"))).noneMatch(l -> l.contains("reactor.") || l.contains("org.springframework"));
    }

    @Test void laAplicacionNoTieneUnDominioPropio() {
        assertThat(Path.of("src/main/java/pedidos/dominio")).doesNotExist();
        assertThat(Path.of("src/main/java/tienda")).doesNotExist();
    }

    static List<String> texto(Path carpeta) throws IOException {
        try (Stream<Path> archivos = Files.walk(carpeta)) {
            return archivos.filter(a -> a.toString().endsWith(".java")).flatMap(a -> {
                try { return Files.readAllLines(a).stream(); } catch (IOException e) { throw new IllegalStateException(e); }
            }).toList();
        }
    }
}
