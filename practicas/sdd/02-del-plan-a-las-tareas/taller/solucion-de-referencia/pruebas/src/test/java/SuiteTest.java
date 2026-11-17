import static org.junit.jupiter.api.Assertions.assertEquals;

import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import org.junit.jupiter.api.Test;

/** Pruebas de aceptación de las etiquetas. -Dbitacora.url=... -Dbitacora.clave=... -Detiquetas=@C1 (opcional). */
class SuiteTest {

    @Test
    void aceptacion() {
        Runner.Builder suite = Runner.path("classpath:etiquetas.feature").outputCucumberJson(false);
        String etiquetas = System.getProperty("etiquetas");
        if (etiquetas != null && !etiquetas.isBlank()) {
            suite.tags(etiquetas.split(";"));
        }
        SuiteResult r = suite.parallel(1);
        System.out.println("ESCENARIOS " + r.getScenarioCount() + " PASAN " + r.getScenarioPassedCount() + " FALLAN " + r.getScenarioFailedCount());
        assertEquals(0, r.getScenarioFailedCount(), String.join("\n", r.getErrors()));
    }
}
