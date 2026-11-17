import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Un servicio externo de pega para los talleres 04, 05 y 06. Uso: java Externo.java puerto [demoraMs]
 *   POST|GET /pagar   tarda la demora actual y responde "pagado"; cuenta las llamadas
 *   GET /notificar    igual que /pagar (el servicio de notificaciones del taller 06)
 *   GET /demora?ms=N  cambia la demora
 *   GET /cuenta       cuántas llamadas a /pagar recibió
 */
public class Externo {

    public static void main(String[] args) throws IOException {
        AtomicLong demora = new AtomicLong(args.length > 1 ? Long.parseLong(args[1]) : 50);
        AtomicInteger llamadas = new AtomicInteger();
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", Integer.parseInt(args[0])), 1000);
        servidor.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        HttpHandler lento = intercambio -> {
            try {
                Thread.sleep(demora.get());
            } catch (InterruptedException interrupcion) {
                Thread.currentThread().interrupt();
            }
            llamadas.incrementAndGet();
            responder(intercambio, "pagado");
        };
        servidor.createContext("/pagar", lento);
        servidor.createContext("/notificar", lento);
        servidor.createContext("/demora", intercambio -> {
            String consulta = intercambio.getRequestURI().getQuery();
            demora.set(Long.parseLong(consulta.substring(consulta.indexOf('=') + 1)));
            responder(intercambio, "demora " + demora.get() + " ms");
        });
        servidor.createContext("/cuenta", intercambio -> responder(intercambio, String.valueOf(llamadas.get())));
        servidor.start();
        System.out.println("externo escuchando en " + args[0] + " con demora " + demora.get() + " ms");
    }

    private static void responder(com.sun.net.httpserver.HttpExchange intercambio, String texto) throws IOException {
        byte[] cuerpo = texto.getBytes(StandardCharsets.UTF_8);
        intercambio.sendResponseHeaders(200, cuerpo.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(cuerpo);
        }
    }
}
