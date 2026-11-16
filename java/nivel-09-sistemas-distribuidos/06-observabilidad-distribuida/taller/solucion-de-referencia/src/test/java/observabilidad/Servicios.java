package observabilidad;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Lanza los servicios A y B como procesos Java independientes, con el classpath de la prueba. */
final class Servicios {

    static final HttpClient HTTP = HttpClient.newHttpClient();

    private Servicios() { }

    static Process lanzar(String clase, String nombre, int puerto, Path yamlReadme, Path registro, String... extra) throws Exception {
        List<String> cmd = new ArrayList<>(List.of(System.getProperty("java.home") + "/bin/java",
                "-cp", System.getProperty("java.class.path"), clase,
                "--spring.application.name=" + nombre, "--server.port=" + puerto,
                "--spring.config.additional-location=file:" + yamlReadme.toAbsolutePath()));
        cmd.addAll(List.of(extra));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(registro.toFile()).start();
        long fin = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        while (System.nanoTime() < fin) {
            if (!p.isAlive()) {
                throw new IllegalStateException(nombre + " murió:\n" + Files.readString(registro));
            }
            try {
                var r = HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/actuator/health")).build(), HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() == 200) {
                    return p;
                }
            } catch (Exception e) {
                // todavía no responde
            }
            Thread.sleep(500);
        }
        p.destroyForcibly();
        throw new IllegalStateException(nombre + " no arrancó a tiempo:\n" + Files.readString(registro));
    }

    static void parar(Process p) throws InterruptedException {
        if (p != null && p.isAlive()) {
            p.destroy();
            if (!p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
            }
        }
    }

    static HttpResponse<String> get(String url) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).build(), HttpResponse.BodyHandlers.ofString());
    }

    static HttpResponse<String> post(String url) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }

    static String leer(Path archivo) throws Exception {
        return Files.exists(archivo) ? Files.readString(archivo) : "";
    }
}
