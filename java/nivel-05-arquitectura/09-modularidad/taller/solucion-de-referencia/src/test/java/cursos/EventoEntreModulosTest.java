package cursos;

import cursos.certificacion.CertificacionApi;
import cursos.inscripciones.CursoCompletado;
import cursos.inscripciones.InscripcionesApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.modulith.test.EnableScenarios;
import org.springframework.modulith.test.Scenario;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnableScenarios
class EventoEntreModulosTest {

    @Autowired InscripcionesApi inscripciones;
    @Autowired CertificacionApi certificacion;

    @Test
    void completarUnCursoEmiteElCertificado(Scenario escenario) {
        inscripciones.inscribir("ana", "JAVA-1");
        escenario.stimulate(() -> inscripciones.completar("ana", "JAVA-1"))
                .andWaitForEventOfType(CursoCompletado.class)
                .matching(e -> e.estudiante().equals("ana"))
                .toArriveAndVerify(e -> assertThat(e.cursoId()).isEqualTo("JAVA-1"));
        escenario.stimulate(() -> { })
                .andWaitForStateChange(() -> certificacion.certificadosDe("ana"), l -> !l.isEmpty())
                .andVerify(l -> assertThat(l).containsExactly("Certificado: Java desde cero"));
    }
}
