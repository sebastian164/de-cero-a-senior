import static org.junit.jupiter.api.Assertions.assertEquals;

import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Prueba de carga básica que reutiliza un .feature existente (bitacora/info.feature, escenarios @humo).
 * Solo corre con -Dcarga=true. Parámetros: -Dusuarios=8 -Diteraciones=25.
 * Cada iteración es una ejecución completa del .feature, así que la medida incluye el costo del cliente Karate.
 */
class CargaTest {

    @Test
    void cargaSobreInfo() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("carga"), "solo con -Dcarga=true");
        int usuarios = Integer.getInteger("usuarios", 8);
        int iteraciones = Integer.getInteger("iteraciones", 25);
        List<Long> tiempos = Collections.synchronizedList(new ArrayList<>());
        List<String> errores = Collections.synchronizedList(new ArrayList<>());
        ExecutorService hilos = Executors.newFixedThreadPool(usuarios);
        long inicio = System.nanoTime();
        List<Future<?>> trabajos = new ArrayList<>();
        for (int u = 0; u < usuarios; u++) {
            trabajos.add(hilos.submit(() -> {
                for (int i = 0; i < iteraciones; i++) {
                    long t0 = System.nanoTime();
                    SuiteResult r = Runner.path("classpath:bitacora/info.feature").tags("@humo").outputHtmlReport(false)
                            .outputCucumberJson(false).parallel(1);
                    tiempos.add((System.nanoTime() - t0) / 1_000_000);
                    if (r.getScenarioFailedCount() > 0) {
                        errores.add(String.join(";", r.getErrors()));
                    }
                }
            }));
        }
        for (Future<?> t : trabajos) {
            t.get();
        }
        hilos.shutdown();
        double segundos = (System.nanoTime() - inicio) / 1e9;
        Collections.sort(tiempos);
        System.out.printf("CARGA usuarios=%d iteraciones=%d total=%d errores=%d p50=%dms p95=%dms p99=%dms max=%dms rendimiento=%.1f/s%n",
                usuarios, iteraciones, tiempos.size(), errores.size(), pct(tiempos, 50), pct(tiempos, 95), pct(tiempos, 99),
                tiempos.get(tiempos.size() - 1), tiempos.size() / segundos);
        assertEquals(0, errores.size(), String.join("\n", errores));
    }

    private static long pct(List<Long> ordenados, int p) {
        return ordenados.get(Math.min(ordenados.size() - 1, (int) Math.ceil(p / 100.0 * ordenados.size()) - 1));
    }
}
