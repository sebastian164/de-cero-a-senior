package tienda.integrador.orquestador.adaptadores.entrada.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.Collectors;
import tienda.integrador.orquestador.aplicacion.Puertos.ConsultarSagas;
import tienda.integrador.plataforma.PublicadorDeSalida;

/**
 * Extremos de operación: GET /sagas/no-terminadas y GET /metricas (texto al estilo Prometheus). Alertas sobre estas métricas:
 * sagas_vencidas > 0 durante 5 minutos, y casos_en_revision > 0.
 */
public final class AdminHttp implements AutoCloseable {

    private final HttpServer servidor;

    public AdminHttp(ConsultarSagas consultas, PublicadorDeSalida salida, String host, int puerto) throws IOException {
        if (puerto == 0) {
            servidor = null;
            return;
        }
        servidor = HttpServer.create(new InetSocketAddress(host, puerto), 0);
        servidor.createContext("/sagas/no-terminadas", ex -> responder(ex, consultas.noTerminadas().stream()
                .map(s -> "{id=" + s.id() + ", estado=" + s.estado() + ", intentos=" + s.intentos() + ", revision=" + s.revision() + "}").collect(Collectors.joining(", ", "[", "]"))));
        servidor.createContext("/metricas", ex -> {
            StringBuilder m = new StringBuilder();
            for (String estado : new String[] {"RESERVANDO", "COBRANDO", "PROGRAMANDO_ENVIO", "REEMBOLSANDO", "LIBERANDO", "COMPLETADA", "CANCELADA"}) {
                m.append("sagas_por_estado{estado=\"").append(estado).append("\"} ").append(consultas.porEstado().getOrDefault(estado, 0L)).append('\n');
            }
            m.append("sagas_vencidas ").append(consultas.vencidas(Duration.ofSeconds(60))).append('\n');
            m.append("casos_en_revision ").append(consultas.enRevision()).append('\n');
            m.append("filas_pendientes_salida{servicio=\"orquestador\"} ").append(salida.pendientes()).append('\n');
            responder(ex, m.toString());
        });
        servidor.start();
    }

    private static void responder(HttpExchange ex, String texto) throws IOException {
        byte[] cuerpo = texto.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(200, cuerpo.length);
        ex.getResponseBody().write(cuerpo);
        ex.close();
    }

    @Override
    public void close() {
        if (servidor != null) servidor.stop(0);
    }

}
