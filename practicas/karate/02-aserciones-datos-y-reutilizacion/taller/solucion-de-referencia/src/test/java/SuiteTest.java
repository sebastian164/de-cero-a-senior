import static org.junit.jupiter.api.Assertions.assertEquals;

import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import org.junit.jupiter.api.Test;

/**
 * Ejecuta la suite de Bitácora. Se controla con propiedades del sistema:
 *   -Dbitacora.clave=...   la clave de la API (obligatoria; no vive en el repositorio)
 *   -Dkarate.env=...       el ambiente: local (por defecto) o contenedor
 *   -Dbitacora.url=...     otra dirección (por defecto, la del ambiente en karate-config.js)
 *   -Detiquetas=@humo      qué etiquetas ejecutar; varias separadas por ";" deben cumplirse todas
 *   -Descenario="..."      un solo escenario, por su nombre
 *   -Dhilos=4              cuántos hilos
 */
class SuiteTest {

    @Test
    void bitacora() {
        Runner.Builder suite = Runner.path("classpath:bitacora")
                .tags(System.getProperty("etiquetas", "~@arranque;~@falla").split(";"))
                .outputCucumberJson(false);
        String ambiente = System.getProperty("karate.env");
        if (ambiente != null && !ambiente.isBlank()) {
            suite.karateEnv(ambiente);
        }
        String escenario = System.getProperty("escenario");
        if (escenario != null && !escenario.isBlank()) {
            suite.scenarioName(escenario);
        }
        SuiteResult resultado = suite.parallel(Integer.getInteger("hilos", 1));
        System.out.println("ESCENARIOS " + resultado.getScenarioCount() + " PASAN " + resultado.getScenarioPassedCount()
                + " FALLAN " + resultado.getScenarioFailedCount() + " EN " + resultado.getDurationMillis() + " ms");
        assertEquals(0, resultado.getScenarioFailedCount(), String.join("\n", resultado.getErrors()));
        assertEquals(true, resultado.getScenarioCount() > 0, "no se ejecutó ningún escenario");
    }
}
