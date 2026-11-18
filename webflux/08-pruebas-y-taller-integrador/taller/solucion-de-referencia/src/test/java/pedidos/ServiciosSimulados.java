package pedidos;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Inventario o pagos simulados con el JDK, con memoria: cuentan las llamadas de cada operación por pedido y, como los reales, son
 * idempotentes (repetir la misma clave no repite el efecto). Uso: java -cp ... pedidos.ServiciosSimulados inventario|pagos puerto latenciaMs
 */
public class ServiciosSimulados {
    final HttpServer servidor;
    private final Map<String, AtomicInteger> llamadas = new ConcurrentHashMap<>();   // "cobrar/e1" -> veces que llegó la petición
    private final Map<String, Boolean> efectos = new ConcurrentHashMap<>();          // "cobrar/e1" -> el efecto se aplicó (una sola vez)

    ServiciosSimulados(HttpServer servidor) { this.servidor = servidor; }

    /** Peticiones recibidas para esa operación y pedido. */
    public int llamadas(String operacion, String pedido) { AtomicInteger n = llamadas.get(operacion + "/" + pedido); return n == null ? 0 : n.get(); }
    /** Veces que el efecto se aplicó de verdad (con idempotencia, 0 o 1). */
    public int efectos(String operacion, String pedido) { return efectos.containsKey(operacion + "/" + pedido) ? 1 : 0; }
    public int puerto() { return servidor.getAddress().getPort(); }
    public void parar() { servidor.stop(0); }

    public static ServiciosSimulados iniciar(String tipo, int puerto, long latenciaMs) throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress(puerto), 500);
        s.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        ServiciosSimulados sim = new ServiciosSimulados(s);
        for (String operacion : tipo.equals("inventario") ? new String[] {"reservar", "liberar"} : new String[] {"cobrar", "reembolsar"}) {
            String ruta = "/" + operacion + "/";
            s.createContext(ruta, x -> {
                String id = x.getRequestURI().getPath().substring(ruta.length());
                sim.llamadas.computeIfAbsent(operacion + "/" + id, k -> new AtomicInteger()).incrementAndGet();
                java.util.concurrent.locks.LockSupport.parkNanos(latenciaMs * 1_000_000L);   // la latencia del servicio simulado
                int codigo = 200;
                if (operacion.equals("reservar") && id.startsWith("sin-stock")) codigo = 409;
                else if (operacion.equals("cobrar") && id.startsWith("rechazado")) codigo = 422;
                else sim.efectos.put(operacion + "/" + id, true);
                x.sendResponseHeaders(codigo, -1);
                x.close();
            });
        }
        s.start();
        return sim;
    }
    public static void main(String[] a) throws IOException {
        iniciar(a[0], Integer.parseInt(a[1]), Long.parseLong(a[2]));
        System.out.println(a[0] + " simulado en el puerto " + a[1] + ", responde en " + a[2] + " ms");
    }
}
