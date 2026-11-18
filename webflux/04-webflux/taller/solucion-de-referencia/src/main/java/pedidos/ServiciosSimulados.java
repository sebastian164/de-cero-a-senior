package pedidos;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/** Inventario o pagos simulados con el JDK. Uso: java -cp ... pedidos.ServiciosSimulados inventario|pagos puerto latenciaMs */
public class ServiciosSimulados {
    public static HttpServer iniciar(String tipo, int puerto, long latenciaMs) throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress(puerto), 500);
        s.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        String ruta = tipo.equals("inventario") ? "/reservar/" : "/cobrar/";
        s.createContext(ruta, x -> {
            String id = x.getRequestURI().getPath().substring(ruta.length());
            try { Thread.sleep(latenciaMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            int codigo = 200;
            if (tipo.equals("inventario") && id.equals("sin-stock")) codigo = 409;
            if (tipo.equals("pagos") && id.equals("rechazado")) codigo = 422;
            x.sendResponseHeaders(codigo, -1);
            x.close();
        });
        s.start();
        return s;
    }
    public static void main(String[] a) throws IOException {
        iniciar(a[0], Integer.parseInt(a[1]), Long.parseLong(a[2]));
        System.out.println(a[0] + " simulado en el puerto " + a[1] + ", responde en " + a[2] + " ms");
    }
}
