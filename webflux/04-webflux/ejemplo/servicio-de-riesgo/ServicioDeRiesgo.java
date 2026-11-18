import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * El "otro servicio": evalúa el riesgo de una cuenta y tarda en responder. Solo usa el JDK.
 * Uso: java ServicioDeRiesgo.java [puerto] [latencia en ms]      (por defecto, 8099 y 300)
 */
public class ServicioDeRiesgo {

    public static void main(String[] args) throws IOException {
        int puerto = args.length > 0 ? Integer.parseInt(args[0]) : 8099;
        long latenciaMs = args.length > 1 ? Long.parseLong(args[1]) : 300;

        HttpServer servidor = HttpServer.create(new InetSocketAddress(puerto), 200);
        servidor.setExecutor(Executors.newVirtualThreadPerTaskExecutor());   // aguanta cientos de peticiones a la vez
        servidor.createContext("/riesgo/", intercambio -> {
            String cuenta = intercambio.getRequestURI().getPath().substring("/riesgo/".length());
            try {
                Thread.sleep(latenciaMs);
            } catch (InterruptedException interrupcion) {
                Thread.currentThread().interrupt();
            }
            byte[] cuerpo = ("{\"cuenta\":\"" + cuenta + "\",\"nivel\":\"BAJO\"}").getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(200, cuerpo.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(cuerpo);
            }
        });
        servidor.start();
        System.out.printf("Servicio de riesgo en el puerto %d, responde en %d ms%n", puerto, latenciaMs);
    }
}
