package reservas;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "reservas", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {
    @ArchTest
    static final ArchRule dominio_no_depende_de_spring_ni_jpa_ni_jdbc = noClasses().that().resideInAPackage("..dominio..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "javax..", "java.sql..", "..infraestructura..", "..aplicacion..");

    @ArchTest
    static final ArchRule aplicacion_no_depende_de_la_infraestructura_ni_de_spring = noClasses().that().resideInAPackage("..aplicacion..")
            .should().dependOnClassesThat().resideInAnyPackage("..infraestructura..", "org.springframework..", "jakarta..", "java.sql..");
}
