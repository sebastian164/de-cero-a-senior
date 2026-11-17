package comun;

import java.io.IOException;

/** Para las pruebas que detienen y arrancan servicios de la infraestructura (el entorno del curso). */
public final class Docker {
    private Docker() {}

    public static void compose(String... args) {
        try {
            String archivo = System.getProperty("infra.compose");
            String proyecto = System.getProperty("infra.proyecto", "nosql");
            String[] base = {"docker", "compose", "-p", proyecto, "-f", archivo};
            String[] cmd = new String[base.length + args.length];
            System.arraycopy(base, 0, cmd, 0, base.length);
            System.arraycopy(args, 0, cmd, base.length, args.length);
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (p.waitFor() != 0) throw new IllegalStateException("docker compose " + String.join(" ", args) + " falló");
        } catch (IOException | InterruptedException e) { throw new RuntimeException(e); }
    }

    public static void parar(String servicio) { compose("stop", servicio); }
    public static void arrancar(String servicio) { compose("start", servicio); }
    /** Congela el contenedor (el socket sigue abierto y nadie contesta): un servicio colgado, no caído. */
    public static void pausar(String servicio) { compose("pause", servicio); }
    public static void reanudar(String servicio) { compose("unpause", servicio); }
}
