package tienda;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReglaDeDependenciasTest {

    private static final JavaClasses CLASES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("tienda");

    static ArchRule reglaPara(String paqueteDelNegocio) {
        return noClasses().that().resideInAPackage(paqueteDelNegocio)
                .should().dependOnClassesThat()
                .resideInAnyPackage("..infraestructura..", "org.springframework..", "..proveedor..");
    }

    @Test
    void elDominioNoDependeDeInfraestructuraNiDeSpring() {
        reglaPara("..dominio..").check(CLASES);
    }

    @Test
    void elServicioOriginalViolaLaMismaRegla() {
        assertThatThrownBy(() -> reglaPara("..antes.service..").check(CLASES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("StripeClient")
                .hasMessageContaining("org.springframework.stereotype.Service");
    }
}
