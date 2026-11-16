package jvm;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JvmTest {

    static final Path TARGET = Path.of("target");

    static Process lanzar(String... argumentos) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.addAll(List.of(argumentos));
        return new ProcessBuilder(cmd).redirectErrorStream(true).start();
    }

    // 1. OutOfMemoryError + volcado + análisis
    @Test
    void agotaElMontonYElVolcadoSenalaLaRaiz() throws Exception {
        Path volcado = TARGET.resolve("fuga.hprof");
        Files.deleteIfExists(volcado);
        Process p = lanzar("-Xmx64m", "-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath=" + volcado,
                "-cp", System.getProperty("java.class.path"), "jvm.RegistroDeSesiones");
        String salida = new String(p.getInputStream().readAllBytes());
        assertThat(p.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(p.exitValue()).isNotZero();
        assertThat(salida).contains("java.lang.OutOfMemoryError: Java heap space").contains("Dumping heap to");
        assertThat(volcado).exists();

        var informe = AnalizadorHprof.analizar(volcado);
        System.out.println(informe.histograma().getFirst() + "\n" + informe.raices().getFirst());
        assertThat(informe.histograma().getFirst().clase()).isEqualTo("byte[]");
        var raiz = informe.raices().getFirst();
        assertThat(raiz.clase() + "." + raiz.campo()).isEqualTo("jvm.RegistroDeSesiones.SESIONES");
        assertThat(raiz.bytesAlcanzables()).isGreaterThan(55L * 1024 * 1024);    // casi todo el montón de 64 MB
    }

    // 2. StackOverflowError
    @Test
    void recursionSinFinDesbordaLaPila() {
        assertThatThrownBy(() -> Recursion.sinFin(0)).isInstanceOf(StackOverflowError.class);
        try { Recursion.medir(); } catch (StackOverflowError e) { System.out.println("profundidad alcanzada: " + Recursion.profundidad); }
        assertThat(Recursion.profundidad).isGreaterThan(1_000);
        // la JVM sigue sana: la pila se libera al desenrollar, el montón no se tocó
        assertThat(new byte[1024]).hasSize(1024);
    }

    // 3. Registros de GC
    @Test
    void registrosDeGc() throws Exception {
        Path log = TARGET.resolve("gc.log");
        Files.deleteIfExists(log);
        Process p = lanzar("-Xmx256m", "-XX:+UseG1GC", "-Xlog:gc:file=" + log, "-cp", System.getProperty("java.class.path"), "jvm.MuchaBasura");
        p.getInputStream().readAllBytes();
        assertThat(p.waitFor(60, TimeUnit.SECONDS)).isTrue();
        List<String> lineas = Files.readAllLines(log);
        var pausa = Pattern.compile("GC\\(\\d+\\) Pause (Young[^)]*\\)?[^\\d]*?) .* ([\\d.]+)ms$");
        int recolecciones = 0;
        double totalMs = 0, maxMs = 0;
        for (String l : lineas) {
            var m = pausa.matcher(l);
            if (m.find()) {
                recolecciones++;
                double ms = Double.parseDouble(m.group(2));
                totalMs += ms;
                maxMs = Math.max(maxMs, ms);
            }
        }
        System.out.printf("G1: %d pausas jóvenes, total %.2f ms, máxima %.2f ms%n", recolecciones, totalMs, maxMs);
        lineas.stream().filter(l -> l.contains("Pause")).limit(3).forEach(System.out::println);
        assertThat(lineas).anyMatch(l -> l.contains("Using G1"));
        assertThat(recolecciones).isGreaterThan(0);
    }

    // 4. Java Flight Recorder: método que más asigna
    @Test
    void jfrEncuentraElMetodoQueMasAsigna() throws Exception {
        Path grabacion = TARGET.resolve("asignaciones.jfr");
        Files.deleteIfExists(grabacion);
        Process p = lanzar("-XX:StartFlightRecording=filename=" + grabacion + ",settings=profile",
                "-cp", System.getProperty("java.class.path"), "jvm.Asignaciones");
        p.getInputStream().readAllBytes();
        assertThat(p.waitFor(60, TimeUnit.SECONDS)).isTrue();

        Map<String, Long> pesoPorMetodo = new HashMap<>();
        for (RecordedEvent e : RecordingFile.readAllEvents(grabacion)) {
            if (!e.getEventType().getName().equals("jdk.ObjectAllocationSample") || e.getStackTrace() == null) continue;
            // primer marco que pertenece a nuestro código
            e.getStackTrace().getFrames().stream()
                    .filter(f -> f.getMethod().getType().getName().startsWith("jvm."))
                    .findFirst()
                    .ifPresent(f -> pesoPorMetodo.merge(f.getMethod().getType().getName() + "." + f.getMethod().getName(),
                            e.getLong("weight"), Long::sum));
        }
        var ranking = pesoPorMetodo.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).toList();
        ranking.forEach(r -> System.out.printf("  %-35s %,d bytes muestreados%n", r.getKey(), r.getValue()));
        assertThat(ranking.getFirst().getKey()).isEqualTo("jvm.Asignaciones.armarReporte");
    }
}
