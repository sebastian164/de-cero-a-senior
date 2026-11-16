package cursos;

import cursosmal.AplicacionMal;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModulosTest {

    static final ApplicationModules MODULOS = ApplicationModules.of(PlataformaCursosApplication.class);

    @Test
    void laEstructuraModularSeRespeta() {
        MODULOS.verify();
        assertThat(MODULOS.stream().map(m -> m.getIdentifier().toString()))
                .containsExactlyInAnyOrder("catalogo", "inscripciones", "certificacion");
    }

    @Test
    void detectaAccesoAlInteriorYCiclos() {
        assertThatThrownBy(() -> ApplicationModules.of(AplicacionMal.class).verify())
                .hasMessageContaining("Cycle detected")
                .hasMessageContaining("non-exposed type cursosmal.reportes.interno.TablaReportes");
    }

    @Test
    void generaDocumentacion() {
        new Documenter(MODULOS).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
