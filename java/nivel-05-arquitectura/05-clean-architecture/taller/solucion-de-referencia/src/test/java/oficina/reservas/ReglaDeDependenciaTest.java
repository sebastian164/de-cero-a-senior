package oficina.reservas;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Regla de dependencia: dominio y aplicación no conocen los círculos externos. */
class ReglaDeDependenciaTest {
    @Test
    void lasDependenciasApuntanHaciaAdentro() {
        var clases = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("oficina.reservas");
        noClasses().that().resideInAnyPackage("..dominio..", "..aplicacion..")
                .should().dependOnClassesThat().resideInAnyPackage("..adaptadores..", "..configuracion..",
                        "org.springframework..", "jakarta..")
                .check(clases);
    }
}
