package banco.transferencias;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;

class ArquitecturaTest {
    static final JavaClasses CLASES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("banco.transferencias");

    @Test
    void elHexagonoNoConoceAdaptadoresNiFrameworks() {
        noClasses().that().resideInAnyPackage("..dominio..", "..aplicacion..")
                .should().dependOnClassesThat().resideInAnyPackage("..adaptador..", "..configuracion..",
                        "org.springframework..", "jakarta..")
                .check(CLASES);
    }

    @Test
    void losAdaptadoresNoSeConocenEntreSi() {
        onionArchitecture()
                .domainModels("..dominio..")
                .applicationServices("..aplicacion..")
                .adapter("web", "..adaptador.entrada.web..")
                .adapter("cli", "..adaptador.entrada.cli..")
                .adapter("memoria", "..adaptador.salida.memoria..")
                .adapter("jpa", "..adaptador.salida.jpa..")
                .withOptionalLayers(true)
                // la configuración es el ensamblador: conoce a todos a propósito
                .ignoreDependency(com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage("..configuracion.."),
                        com.tngtech.archunit.base.DescribedPredicate.alwaysTrue())
                .check(CLASES);
    }
}
