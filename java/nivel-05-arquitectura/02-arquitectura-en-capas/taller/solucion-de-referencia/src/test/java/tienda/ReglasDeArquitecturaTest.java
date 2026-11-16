package tienda;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReglasDeArquitecturaTest {

    static final ArchRule NINGUN_CONTROLADOR_USA_UN_REPOSITORIO = noClasses()
            .that().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
            .because("el controlador habla con el servicio, no con la persistencia");

    private static JavaClasses de(String paquete) {
        return new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages(paquete);
    }

    @Test
    void laVersionCorregidaCumpleLaRegla() {
        NINGUN_CONTROLADOR_USA_UN_REPOSITORIO.check(de("tienda.despues"));
    }

    @Test
    void laVersionOriginalLaViola() {
        assertThatThrownBy(() -> NINGUN_CONTROLADOR_USA_UN_REPOSITORIO.check(de("tienda.antes")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("PedidoController")
                .hasMessageContaining("PedidoRepository");
    }
}
