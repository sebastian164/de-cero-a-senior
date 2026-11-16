package mensajeria.despacho;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class NucleoTest {
    @Test
    void dominioYAplicacionSoloDependenDeJavaYDeSiMismos() {
        classes().that().resideInAnyPackage("..dominio..", "..aplicacion..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("..dominio..", "..aplicacion..", "java..")
                .check(new ClassFileImporter().importPackages("mensajeria.despacho"));
    }
}
