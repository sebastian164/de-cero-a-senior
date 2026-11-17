package comun;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Un proyecto de Docker Compose que las pruebas levantan, detienen, pausan y matan. */
public final class Compose {
    private final String proyecto, archivo;
    private final Map<String, String> entorno;

    public Compose(String proyecto, String archivo, Map<String, String> entorno) { this.proyecto = proyecto; this.archivo = archivo; this.entorno = entorno; }

    public String ejecutar(String... args) {
        List<String> cmd = new ArrayList<>(List.of("docker", "compose", "-p", proyecto, "-f", archivo));
        cmd.addAll(List.of(args));
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true).directory(new File(archivo).getParentFile());
            pb.environment().putAll(entorno);
            Process p = pb.start();
            String salida = new String(p.getInputStream().readAllBytes());
            if (p.waitFor() != 0) throw new IllegalStateException("docker compose " + String.join(" ", args) + " falló:\n" + salida);
            return salida;
        } catch (IOException | InterruptedException e) { throw new RuntimeException(e); }
    }

    public void arriba() { ejecutar("up", "-d", "--build", "--wait"); }
    public void abajo() { ejecutar("down", "-v", "--remove-orphans"); }
    public void parar(String servicio) { ejecutar("stop", servicio); }
    public void arrancar(String servicio) { ejecutar("start", servicio); }
    public void matar(String servicio) { ejecutar("kill", "-s", "SIGKILL", servicio); }
    public void pausar(String servicio) { ejecutar("pause", servicio); }
    public void reanudar(String servicio) { ejecutar("unpause", servicio); }
    public String logs(String servicio) { return ejecutar("logs", "--no-color", servicio); }
}
