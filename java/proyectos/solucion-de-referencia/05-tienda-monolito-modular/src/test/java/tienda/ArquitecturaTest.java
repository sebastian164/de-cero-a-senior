package tienda;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import org.springframework.modulith.core.ApplicationModules;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

@AnalyzeClasses(packages = "tienda", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {
    @ArchTest
    static final ArchRule sin_dependencias_ciclicas_entre_modulos = slices().matching("tienda.(*)..").should().beFreeOfCycles();

    static final List<String> MODULOS = List.of("catalogo", "inventario", "pedidos", "pagos", "notificaciones", "eventos");

    /** Ninguna clase de fuera de un módulo depende del paquete interno de ese módulo. */
    @ArchTest
    static void nadie_usa_el_interior_de_otro_modulo(JavaClasses clases) {
        for (String m : MODULOS) {
            noClasses().that().resideOutsideOfPackage("tienda." + m + "..")
                    .should().dependOnClassesThat().resideInAPackage("tienda." + m + ".interno..")
                    .allowEmptyShould(true).check(clases);
        }
    }

    /** La regla anterior no es vacía: hay clases en paquetes internos, y de verdad se detecta que alguien las use. */
    @ArchTest
    static void la_regla_del_interior_no_es_vacia(JavaClasses clases) {
        assertThat(clases.that(resideInAPackage("tienda.pagos.interno..")).size()).isPositive();
        assertThat(clases.that(resideInAPackage("tienda.pedidos.interno..")).size()).isPositive();
        // Prueba negativa: la misma regla aplicada a un conjunto donde sí se viola (pedidos usa pedidos.interno; visto desde "tienda.pagos..").
        var violacion = noClasses().that().resideOutsideOfPackage("tienda.pedidos..")
                .should().dependOnClassesThat().resideInAPackage("tienda.pedidos.interno..");
        violacion.check(clases);
        var invertida = noClasses().that().resideInAPackage("tienda.pedidos..")
                .should().dependOnClassesThat().resideInAPackage("tienda.pedidos.interno..");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> invertida.check(clases)).isInstanceOf(AssertionError.class);
    }

    /** Spring Modulith: módulos = subpaquetes directos de tienda; se prohíben ciclos y el acceso a tipos internos. */
    @Test
    void spring_modulith_verifica_la_estructura_de_modulos() {
        var modulos = ApplicationModules.of(AplicacionTienda.class);
        modulos.verify();
        assertThat(modulos.stream().map(m -> m.getIdentifier().toString())).containsAll(MODULOS);
    }

    /** Cada módulo solo nombra sus propias tablas en su código (acceso a tablas ajenas = fallo). */
    @Test
    void ningun_modulo_toca_tablas_de_otro() throws Exception {
        Map<String, List<String>> tablas = Map.of(
                "catalogo", List.of("catalogo_"), "inventario", List.of("inventario_"), "pedidos", List.of("pedidos_"),
                "pagos", List.of("pagos_"), "notificaciones", List.of("notificaciones_"), "eventos", List.of("eventos_"));
        Path raiz = Path.of("src/main/java/tienda");
        for (var modulo : tablas.keySet()) {
            try (Stream<Path> fs = Files.walk(raiz.resolve(modulo))) {
                for (Path f : fs.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String codigo = Files.readString(f);
                    for (var otro : tablas.entrySet()) {
                        if (otro.getKey().equals(modulo)) continue;
                        for (String prefijo : otro.getValue()) assertThat(codigo).as(f + " no debe tocar " + prefijo + "*").doesNotContain(prefijo);
                    }
                }
            }
        }
    }
}
