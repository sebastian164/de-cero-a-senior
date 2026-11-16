package biblioteca;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;

class AnillosTest {
    static final JavaClasses CLASES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("biblioteca");

    @Test
    void elNucleoSoloUsaElPropioNucleoYJava() {
        classes().that().resideInAPackage("biblioteca.nucleo..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("biblioteca.nucleo..", "java..")
                .check(CLASES);
    }

    @Test
    void anillosDeOnion() {
        onionArchitecture()
                .domainModels("biblioteca.nucleo.modelo..")
                .domainServices("biblioteca.nucleo.servicios..")
                .applicationServices("biblioteca.aplicacion..")
                .adapter("memoria", "biblioteca.infraestructura..")
                .check(CLASES);
    }
}
