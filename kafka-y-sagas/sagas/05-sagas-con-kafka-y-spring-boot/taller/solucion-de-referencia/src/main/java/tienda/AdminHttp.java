package tienda;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Extremos de operación del orquestador (paso 11): GET /sagas/no-terminadas y GET /metricas (texto al estilo Prometheus).
 * Alertas que se definen sobre estas métricas (ver ANALISIS.md): sagas_vencidas > 0 durante 5 minutos, y casos_en_revision > 0.
 */
public class AdminHttp implements AutoCloseable {

    private final HttpServer servidor;

    public AdminHttp(Orquestador orquestador, Salida salida, JdbcTemplate jdbc, int puerto) throws IOException {
        if (puerto == 0) {
            servidor = null;
            return;
        }
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", puerto), 0);
        servidor.createContext("/sagas/no-terminadas", ex -> responder(ex, orquestador.noTerminadas().toString()));
        servidor.createContext("/metricas", ex -> {
            StringBuilder m = new StringBuilder();
            for (String estado : new String[] {"RESERVANDO", "COBRANDO", "PROGRAMANDO_ENVIO", "REEMBOLSANDO", "LIBERANDO", "COMPLETADA", "CANCELADA"}) {
                m.append("sagas_por_estado{estado=\"").append(estado).append("\"} ").append(orquestador.sagasPorEstado().getOrDefault(estado, 0L)).append('\n');
            }
            m.append("sagas_vencidas ").append(orquestador.vencidas(60)).append('\n');
            m.append("casos_en_revision ").append(orquestador.enRevision()).append('\n');
            m.append("filas_pendientes_salida{servicio=\"orquestador\"} ").append(salida.pendientes()).append('\n');
            responder(ex, m.toString());
        });
        servidor.start();
    }

    private static void responder(com.sun.net.httpserver.HttpExchange ex, String texto) throws IOException {
        byte[] cuerpo = texto.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(200, cuerpo.length);
        ex.getResponseBody().write(cuerpo);
        ex.close();
    }

    @Override
    public void close() {
        if (servidor != null) servidor.stop(0);
    }

    static Map<String, String> nada() {
        return Map.of();
    }
}
