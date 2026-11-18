package banco;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Las decisiones de la sesión 07, comprobadas: si alguien las rompe sin querer, la construcción falla. */
class ArquitecturaTest {

    static final Path DOMINIO = Path.of("../../../../hexagonal/proyecto/src/banco/dominio");
    static final Path APLICACION_REACTIVA = Path.of("../../../07-el-banco-hexagonal-reactivo/ejemplo/despues/src/main/java/banco/aplicacion/reactiva");

    @Test
    void elDominioNoConoceAReactorNiASpring() throws IOException {
        assertThat(importsDe(DOMINIO)).isNotEmpty()
                .noneMatch(linea -> linea.contains("reactor.") || linea.contains("org.springframework."));
    }

    @Test
    void laAplicacionReactivaConoceAReactorPeroNoASpring() throws IOException {
        List<String> imports = importsDe(APLICACION_REACTIVA);
        assertThat(imports).anyMatch(linea -> linea.contains("reactor.core.publisher"));
        assertThat(imports).noneMatch(linea -> linea.contains("org.springframework."));
    }

    private static List<String> importsDe(Path carpeta) throws IOException {
        try (Stream<Path> archivos = Files.walk(carpeta)) {
            return archivos.filter(archivo -> archivo.toString().endsWith(".java"))
                    .flatMap(archivo -> {
                        try {
                            return Files.readAllLines(archivo).stream();
                        } catch (IOException error) {
                            throw new IllegalStateException(error);
                        }
                    })
                    .filter(linea -> linea.startsWith("import "))
                    .toList();
        }
    }
}
