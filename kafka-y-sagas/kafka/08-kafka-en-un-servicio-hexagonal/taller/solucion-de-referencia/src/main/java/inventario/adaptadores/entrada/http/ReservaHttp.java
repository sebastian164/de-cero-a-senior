package inventario.adaptadores.entrada.http;

import com.sun.net.httpserver.HttpServer;
import inventario.aplicacion.ReservarPorPedido;
import inventario.aplicacion.ReservarPorPedido.Orden;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

/** Segundo adaptador de entrada de la misma operación (paso 10): POST /reservas?evento=..&pedido=..&producto=..&cantidad=.. */
public final class ReservaHttp implements AutoCloseable {

    private final HttpServer servidor;

    public ReservaHttp(ReservarPorPedido puerto) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/reservas", ex -> {
            Map<String, String> q = new HashMap<>();
            for (String par : ex.getRequestURI().getQuery().split("&")) q.put(par.split("=")[0], par.split("=")[1]);
            var r = puerto.reservar(new Orden(q.get("evento"), q.get("pedido"), q.get("producto"), Integer.parseInt(q.get("cantidad"))));
            byte[] cuerpo = r.name().getBytes();
            ex.sendResponseHeaders(200, cuerpo.length);
            ex.getResponseBody().write(cuerpo);
            ex.close();
        });
        servidor.start();
    }

    public int puerto() {
        return servidor.getAddress().getPort();
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
