package concurrencia;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrenciaTest {

    static final int HILOS = 10, POR_HILO = 1_000_000;

    // 1
    @Test
    void contadorInseguroPierdeIncrementosYTresCorrecciones() throws Exception {
        long inseguro = Contadores.ejecutar(new Contadores.Inseguro(), HILOS, POR_HILO);
        System.out.println("inseguro: " + inseguro + " de " + (long) HILOS * POR_HILO);
        assertThat(inseguro).isLessThan((long) HILOS * POR_HILO);
        assertThat(Contadores.ejecutar(new Contadores.ConSynchronized(), HILOS, POR_HILO)).isEqualTo((long) HILOS * POR_HILO);
        assertThat(Contadores.ejecutar(new Contadores.ConAtomico(), HILOS, POR_HILO)).isEqualTo((long) HILOS * POR_HILO);
        assertThat(Contadores.ejecutar(new Contadores.ConLongAdder(), HILOS, POR_HILO)).isEqualTo((long) HILOS * POR_HILO);
    }

    // 2
    @Test
    void interbloqueoDiagnosticadoConJcmdYCorregido() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String jcmd = Path.of(System.getProperty("java.home"), "bin", "jcmd").toString();
        String cp = System.getProperty("java.class.path");

        Process bloqueado = new ProcessBuilder(java, "-cp", cp, "concurrencia.Interbloqueo").start();
        try {
            var lector = new BufferedReader(new InputStreamReader(bloqueado.getInputStream()));
            assertThat(lector.readLine()).startsWith("listo pid=");
            Thread.sleep(1_000);
            assertThat(bloqueado.isAlive()).as("sigue vivo: está bloqueado").isTrue();
            Process volcado = new ProcessBuilder(jcmd, String.valueOf(bloqueado.pid()), "Thread.print").redirectErrorStream(true).start();
            String salida = new String(volcado.getInputStream().readAllBytes());
            volcado.waitFor(30, TimeUnit.SECONDS);
            System.out.println(salida.substring(salida.indexOf("Found one Java-level deadlock")));
            assertThat(salida).contains("Found one Java-level deadlock")
                    .contains("\"transferencia-1\"").contains("\"transferencia-2\"")
                    .contains("waiting to lock monitor");
        } finally {
            bloqueado.destroyForcibly().waitFor();
        }

        Process corregido = new ProcessBuilder(java, "-cp", cp, "concurrencia.Interbloqueo", "corregido").start();
        assertThat(corregido.waitFor(10, TimeUnit.SECONDS)).as("termina").isTrue();
        assertThat(new String(corregido.getInputStream().readAllBytes())).contains("transferencia-1 terminó", "transferencia-2 terminó", "fin");
    }

    // 3
    @Test
    void tresServiciosEnParaleloTardanLoQueElMasLento() {
        try (var pool = Executors.newFixedThreadPool(3)) {
            ServiciosSimulados.consultar(pool);                         // calentamiento
            long inicio = System.nanoTime();
            var resumen = ServiciosSimulados.consultar(pool);
            long ms = (System.nanoTime() - inicio) / 1_000_000;
            System.out.println("CompletableFuture: " + ms + " ms -> " + resumen);
            assertThat(resumen).isEqualTo(new ServiciosSimulados.Resumen("Ana", "3 pedidos", "120 puntos"));
            assertThat(ms).isBetween(795L, 1_000L);                     // ~800, no 1.600
        }
    }

    // 4
    @Test
    void diezMilTareasConPoolFijoYConHilosVirtuales() {
        Duration virtuales = TareasMasivas.medir(Executors.newVirtualThreadPerTaskExecutor(), 10_000);
        Duration fijo = TareasMasivas.medir(Executors.newFixedThreadPool(200), 10_000);
        System.out.println("hilos virtuales: " + virtuales.toMillis() + " ms; pool fijo de 200: " + fijo.toMillis() + " ms");
        assertThat(virtuales).isLessThan(Duration.ofSeconds(3));
        assertThat(fijo).isGreaterThanOrEqualTo(Duration.ofSeconds(50));   // 10.000 / 200 = 50 tandas de 1 s
    }

    // 5
    @Test
    void unSingletonConEstadoMezclaPeticiones() throws Exception {
        var servicio = new ServicioConEstado();
        var cruces = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 20_000; i++) {
                String cliente = "cliente-" + i;
                pool.submit(() -> { if (!servicio.saludar(cliente).equals("Hola " + cliente)) cruces.incrementAndGet(); });
            }
        }
        System.out.println("respuestas con el cliente de otra petición: " + cruces.get() + " de 20000");
        assertThat(cruces.get()).isPositive();
    }
}
