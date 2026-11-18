package pedidos;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pedidos.aplicacion.Errores.*;
import pedidos.aplicacion.ServicioDePedidos;
import pedidos.aplicacion.puertos.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.aplicacion.PedidoNoEncontrado;
import tienda.dominio.*;

/** Pruebas de la aplicación con StepVerifier y dobles en memoria. Sin Spring, sin base, sin Thread.sleep. */
class AplicacionTest {
    final Memoria m = new Memoria();
    final AtomicInteger reservas = new AtomicInteger(), liberaciones = new AtomicInteger(), cobros = new AtomicInteger(), reembolsos = new AtomicInteger();
    final List<String> eventos = new ArrayList<>();
    boolean pagoRechazado, inventarioSinStock, reembolsoFalla; Duration esperaDePagos = Duration.ofMillis(10);

    final RepositorioDePedidos repo = new RepositorioDePedidos() {
        public Mono<Pedido> buscar(String id) { return Mono.defer(() -> Mono.justOrEmpty(m.pedidos.get(id)).map(p -> Pedido.existente(p.id(), p.cliente(), p.lineas(), p.estado(), p.creadoEn()))); }
        public Mono<Void> guardar(Pedido p, EstadoDePedido leido) { return Mono.fromRunnable(() -> m.guardarPedido(p, leido)); }
    };
    final Almacen almacen = new Almacen() {
        public Mono<Existencias> existenciasDe(String p) { return Mono.defer(() -> Mono.justOrEmpty(m.stock.get(p)).map(n -> Existencias.de(p, n))); }
        public Mono<Void> guardar(Existencias e) { return Mono.fromRunnable(() -> m.stock.put(e.producto(), e.unidades())); }
    };
    final UnidadDeTrabajo unidad = new UnidadDeTrabajo() {
        public <T> Mono<T> ejecutar(Supplier<Mono<T>> trabajo) {
            return Mono.defer(() -> { Map<String, Object> antes = m.instantanea(); return trabajo.get().doOnError(e -> m.revertir(antes)); });
        }
    };
    final Inventario inventario = new Inventario() {
        public Mono<Void> reservar(Pedido p) { return Mono.defer(() -> { reservas.incrementAndGet(); return inventarioSinStock ? Mono.error(new SinExistenciasEnInventario(p.id())) : Mono.empty(); }); }
        public Mono<Void> liberar(Pedido p) { return Mono.fromRunnable(liberaciones::incrementAndGet); }
    };
    final Pagos pagos = new Pagos() {
        public Mono<Void> cobrar(Pedido p) {
            return Mono.defer(() -> {
                cobros.incrementAndGet();
                return pagoRechazado ? Mono.<Void>error(new PagoRechazado(p.id())) : Mono.<Void>empty().delaySubscription(esperaDePagos);
            });
        }
        public Mono<Void> reembolsar(Pedido p) {
            return Mono.defer(() -> { reembolsos.incrementAndGet(); return reembolsoFalla ? Mono.<Void>error(new ServicioExternoNoDisponible("pagos", null)) : Mono.<Void>empty(); });
        }
    };
    final Eventos eventosEnMemoria = new Eventos() {
        public void emitir(String id, String e) { eventos.add(e); }
        public void terminar(String id) { }
        public Flux<String> flujo(String id) { return Flux.empty(); }
    };
    ServicioDePedidos servicio;

    @BeforeEach void datos() {
        m.pedidos.put("p1", Datos.pedido("p1", EstadoDePedido.PENDIENTE, "a", "b"));
        m.stock.put("a", 5); m.stock.put("b", 5);
        servicio = new ServicioDePedidos(repo, almacen, unidad, inventario, pagos, eventosEnMemoria);
    }

    void sinCompensacion() { assertEquals(0, liberaciones.get(), "no se libera"); assertEquals(0, reembolsos.get(), "no se reembolsa"); }

    @Test void casoFeliz() {
        StepVerifier.create(servicio.confirmar("p1")).assertNext(p -> assertEquals(EstadoDePedido.CONFIRMADO, p.estado())).verifyComplete();
        assertEquals(EstadoDePedido.CONFIRMADO, m.pedidos.get("p1").estado());
        assertEquals(3, m.stock.get("a")); assertEquals(3, m.stock.get("b"));
        assertEquals(List.of("RESERVADO", "COBRADO", "CONFIRMADO"), eventos);
        assertEquals(1, reservas.get()); assertEquals(1, cobros.get()); sinCompensacion();
    }

    @Test void pedidoInexistenteNoTocaNadaExterno() {
        StepVerifier.create(servicio.confirmar("nada")).expectError(PedidoNoEncontrado.class).verify();
        assertEquals(0, reservas.get()); assertEquals(0, cobros.get()); sinCompensacion();
    }

    @Test void yaConfirmadoLoDecideElDominioAntesDeTocarNadaExterno() {
        m.pedidos.put("p1", Datos.pedido("p1", EstadoDePedido.CONFIRMADO, "a"));
        StepVerifier.create(servicio.confirmar("p1")).expectError(ReglaDeNegocioViolada.class).verify();
        assertEquals(0, reservas.get()); assertEquals(0, cobros.get()); sinCompensacion();
    }

    @Test void elInventarioSinExistenciasNoCobraNiCompensa() {
        inventarioSinStock = true;
        StepVerifier.create(servicio.confirmar("p1")).expectError(SinExistenciasEnInventario.class).verify();
        assertEquals(0, cobros.get()); sinCompensacion();
        assertEquals(List.of("FALLIDO"), eventos);
    }

    @Test void pagoRechazadoLiberaLaReservaYNoReembolsa() {
        pagoRechazado = true;
        StepVerifier.create(servicio.confirmar("p1")).expectError(PagoRechazado.class).verify();
        assertEquals(1, reservas.get()); assertEquals(1, cobros.get());
        assertEquals(1, liberaciones.get(), "la reserva se devuelve"); assertEquals(0, reembolsos.get(), "no hubo cobro que devolver");
        assertEquals(5, m.stock.get("a")); assertEquals(EstadoDePedido.PENDIENTE, m.pedidos.get("p1").estado());
        assertEquals(List.of("RESERVADO", "FALLIDO"), eventos);
    }

    @Test void sinExistenciasPropiasDespuesDeCobrarReembolsaYLibera() {
        m.stock.put("b", 1);                              // el inventario externo dijo que sí, pero nuestra base no alcanza
        StepVerifier.create(servicio.confirmar("p1")).expectError(ReglaDeNegocioViolada.class).verify();
        assertEquals(1, cobros.get(), "se cobró antes de descubrirlo");
        assertEquals(1, reembolsos.get(), "y se devolvió"); assertEquals(1, liberaciones.get());
        assertEquals(5, m.stock.get("a")); assertEquals(EstadoDePedido.PENDIENTE, m.pedidos.get("p1").estado());
    }

    @Test void unConflictoSeReintentaYCobraUnaSolaVez() {
        m.conflictosPorProvocar = 1;
        StepVerifier.create(servicio.confirmar("p1")).expectNextCount(1).verifyComplete();
        assertEquals(1, reservas.get(), "el reintento repite solo la transacción corta"); assertEquals(1, cobros.get(), "y no vuelve a cobrar");
        assertEquals(3, m.stock.get("a")); sinCompensacion();
    }

    @Test void tresConflictosSeguidosSeRindenYDeshacenLoExterno() {
        m.conflictosPorProvocar = 3;
        StepVerifier.create(servicio.confirmar("p1")).expectError(ConflictoDeConcurrencia.class).verify();
        assertEquals(1, cobros.get()); assertEquals(1, reembolsos.get()); assertEquals(1, liberaciones.get());
        assertEquals(EstadoDePedido.PENDIENTE, m.pedidos.get("p1").estado()); assertEquals(5, m.stock.get("a"));
    }

    @Test void siOtraPeticionGanoElCobroYLaReservaSonSuyosYNoSeTocan() {
        m.conflictosPorProvocar = 1;
        m.escrituraAjena = () -> m.pedidos.put("p1", Datos.pedido("p1", EstadoDePedido.CONFIRMADO, "a", "b"));   // otro confirmó justo ahora y su commit sobrevive a nuestra reversión
        StepVerifier.create(servicio.confirmar("p1")).expectError(ReglaDeNegocioViolada.class).verify();
        sinCompensacion();
    }

    @Test void unReembolsoQueFallaNoOcultaElErrorOriginal() {
        m.stock.put("b", 1); reembolsoFalla = true;
        StepVerifier.create(servicio.confirmar("p1")).expectError(ReglaDeNegocioViolada.class).verify();
        assertEquals(1, reembolsos.get());
    }

    @Test void pagosLentosFallanConMiExcepcionConTiempoVirtual() {
        esperaDePagos = Duration.ofSeconds(30);
        long t0 = System.nanoTime();
        StepVerifier.withVirtualTime(() -> servicio.confirmar("p1"))
                .thenAwait(Duration.ofSeconds(4))
                .expectError(ServicioExternoNoDisponible.class)
                .verify();
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 1000, "con tiempo virtual debe durar menos de un segundo");
        assertEquals(1, reembolsos.get(), "el cobro quedó dudoso: se pide el reembolso (idempotente)"); assertEquals(1, liberaciones.get());
    }
}
