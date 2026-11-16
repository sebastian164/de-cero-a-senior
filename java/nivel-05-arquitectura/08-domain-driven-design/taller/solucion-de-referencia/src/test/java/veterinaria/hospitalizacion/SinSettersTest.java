package veterinaria.hospitalizacion;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

class SinSettersTest {
    @Test
    void elAgregadoNoTieneSettersNiMutadoresPublicosEnLaEntidadInterna() {
        var clases = new ClassFileImporter().importPackages("veterinaria.hospitalizacion");
        noMethods().that().areDeclaredInClassesThat().resideInAPackage("veterinaria.hospitalizacion")
                .should().haveNameStartingWith("set").check(clases);
        methods().that().areDeclaredIn(Prescripcion.class).and().haveNameMatching("registrarAdministracion|suspender")
                .should().notBePublic().check(clases);
    }
}
