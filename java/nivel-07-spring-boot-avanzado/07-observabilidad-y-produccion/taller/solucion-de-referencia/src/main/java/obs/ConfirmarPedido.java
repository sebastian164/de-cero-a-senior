package obs;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** Servicio A: confirma el pedido llamando a B. Dos métricas de negocio y una técnica propia aquí. */
@Service
class ConfirmarPedido {
    private static final Logger log = LoggerFactory.getLogger(ConfirmarPedido.class);

    private final Counter confirmados;
    private final Counter rechazados;
    private final AtomicInteger enProceso = new AtomicInteger();
    private final RestClient clienteB;

    ConfirmarPedido(MeterRegistry registro, RestClient.Builder constructor, @Value("${app.destino:http://localhost:8080}") String destino) {
        this.confirmados = Counter.builder("pedidos.confirmados").description("Pedidos confirmados").register(registro);
        this.rechazados = Counter.builder("pedidos.rechazados").description("Pedidos rechazados").tag("motivo", "pago").register(registro);
        Gauge.builder("pedidos.en.proceso", enProceso, AtomicInteger::get).description("Pedidos en curso (saturación)").register(registro);
        this.clienteB = constructor.baseUrl(destino).build();
    }

    String ejecutar(long id) {
        enProceso.incrementAndGet();
        try {
            String cobro = clienteB.get().uri("/b/cobro/{id}", id).retrieve().body(String.class);
            confirmados.increment();
            log.info("Pedido confirmado pedidoId={}", id);
            return "confirmado:" + cobro;
        } catch (RuntimeException e) {
            rechazados.increment();
            log.warn("Pedido rechazado pedidoId={}", id);
            throw e;
        } finally {
            enProceso.decrementAndGet();
        }
    }
}
