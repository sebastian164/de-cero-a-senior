package pedidos;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Mono;

/** Dobles en memoria. Cada contador sube al SUSCRIBIRSE (Mono.defer), no al armar la cadena. */
final class Dobles {
    static final class PedidosEnMemoria implements Pedidos {
        final Map<String, Pedido> datos = new ConcurrentHashMap<>();
        final AtomicInteger guardados = new AtomicInteger();
        public Mono<Pedido> buscar(String id) { return Mono.defer(() -> Mono.justOrEmpty(datos.get(id))); }
        public Mono<Pedido> guardar(Pedido p) {
            return Mono.defer(() -> { guardados.incrementAndGet(); datos.put(p.id(), p); return Mono.just(p).delayElement(Duration.ofMillis(10)); });
        }
    }
    static final class InventarioEnMemoria implements Inventario {
        final Duration espera; final AtomicInteger llamadas = new AtomicInteger();
        InventarioEnMemoria(Duration espera) { this.espera = espera; }
        public Mono<Reserva> reservar(Pedido p) {
            return Mono.defer(() -> { llamadas.incrementAndGet(); return Mono.just(new Reserva(p.id())).delayElement(espera); });
        }
    }
    static final class AntifraudeEnMemoria implements Antifraude {
        final Duration espera; final boolean aprueba; final AtomicInteger llamadas = new AtomicInteger();
        AntifraudeEnMemoria(Duration espera, boolean aprueba) { this.espera = espera; this.aprueba = aprueba; }
        public Mono<Boolean> aprobar(Pedido p) {
            return Mono.defer(() -> { llamadas.incrementAndGet(); return Mono.just(aprueba).delayElement(espera); });
        }
    }
    static final class PagosEnMemoria implements Pagos {
        final Duration espera; final AtomicInteger cobros = new AtomicInteger();
        PagosEnMemoria(Duration espera) { this.espera = espera; }
        public Mono<Cobro> cobrar(Pedido p) {
            return Mono.defer(() -> { cobros.incrementAndGet(); return Mono.just(new Cobro(p.id(), p.monto())).delayElement(espera); });
        }
    }
}
