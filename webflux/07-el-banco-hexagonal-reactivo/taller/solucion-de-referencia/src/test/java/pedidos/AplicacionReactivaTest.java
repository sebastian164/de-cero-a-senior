package pedidos;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pedidos.aplicacion.reactiva.ServicioDePedidos;
import pedidos.aplicacion.reactiva.puertos.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.aplicacion.PedidoNoEncontrado;
import tienda.dominio.*;

/** Las mismas pruebas, reescritas para los puertos reactivos: StepVerifier en vez de asserts directos, Mono en cada doble. */
class AplicacionReactivaTest {
    final Memoria m = new Memoria();
    final RepositorioDePedidos repo = new RepositorioDePedidos() {
        public Mono<Pedido> buscar(String id) { return Mono.defer(() -> Mono.justOrEmpty(m.pedidos.get(id)).map(p -> Pedido.existente(p.id(), p.cliente(), p.lineas(), p.estado(), p.creadoEn()))); }
        public Mono<Void> guardar(Pedido p, EstadoDePedido leido) { return Mono.fromRunnable(() -> m.guardarPedido(p, leido)); }
    };
    final Inventario inventario = new Inventario() {
        public Mono<Existencias> existenciasDe(String p) { return Mono.defer(() -> Mono.justOrEmpty(m.stock.get(p)).map(n -> Existencias.de(p, n))); }
        public Mono<Void> guardar(Existencias e) { return Mono.fromRunnable(() -> m.stock.put(e.producto(), e.unidades())); }
    };
    final UnidadDeTrabajo unidad = new UnidadDeTrabajo() {
        public <T> Mono<T> ejecutar(Supplier<Mono<T>> trabajo) {
            return Mono.defer(() -> { Map<String, Object> antes = m.instantanea(); return trabajo.get().doOnError(e -> m.revertir(antes)); });
        }
    };
    ServicioDePedidos servicio;

    @BeforeEach void datos() {
        m.pedidos.put("p1", Datos.pedido("p1", EstadoDePedido.PENDIENTE, "a", "b"));
        m.stock.put("a", 5); m.stock.put("b", 5);
        servicio = new ServicioDePedidos(repo, inventario, unidad);
    }

    @Test void casoFeliz() {
        StepVerifier.create(servicio.confirmar("p1")).assertNext(p -> assertEquals(EstadoDePedido.CONFIRMADO, p.estado())).verifyComplete();
        assertEquals(EstadoDePedido.CONFIRMADO, m.pedidos.get("p1").estado());
        assertEquals(3, m.stock.get("a")); assertEquals(3, m.stock.get("b"));
    }
    @Test void pedidoInexistente() { StepVerifier.create(servicio.confirmar("nada")).expectError(PedidoNoEncontrado.class).verify(); }
    @Test void sinExistenciasRevierteTodo() {
        m.stock.put("b", 1);
        StepVerifier.create(servicio.confirmar("p1")).expectError(ReglaDeNegocioViolada.class).verify();
        assertEquals(5, m.stock.get("a")); assertEquals(EstadoDePedido.PENDIENTE, m.pedidos.get("p1").estado());
    }
    @Test void yaConfirmadoLoDecideElDominio() {
        StepVerifier.create(servicio.confirmar("p1")).expectNextCount(1).verifyComplete();
        StepVerifier.create(servicio.confirmar("p1")).expectErrorSatisfies(e -> { assertInstanceOf(ReglaDeNegocioViolada.class, e); assertTrue(e.getMessage().contains("CONFIRMADO")); }).verify();
        assertEquals(3, m.stock.get("a"));
    }
    @Test void unConflictoSeReintenta() {
        m.conflictosPorProvocar = 1;
        StepVerifier.create(servicio.confirmar("p1")).expectNextCount(1).verifyComplete();
        assertEquals(3, m.stock.get("a"));
    }
    @Test void tresConflictosSeRinden() {
        m.conflictosPorProvocar = 3;
        StepVerifier.create(servicio.confirmar("p1")).expectError(ConflictoDeConcurrencia.class).verify();
        assertEquals(5, m.stock.get("a"));
    }
}
