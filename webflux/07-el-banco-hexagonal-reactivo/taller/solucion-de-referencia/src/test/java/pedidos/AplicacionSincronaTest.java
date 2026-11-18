package pedidos;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pedidos.aplicacion.sincrona.ServicioDePedidos;
import pedidos.aplicacion.sincrona.puertos.*;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.aplicacion.PedidoNoEncontrado;
import tienda.dominio.*;

/** Las pruebas del servicio SÍNCRONO (opciones A y B) con dobles en memoria. Con la opción C ninguna de estas sirve: ver ANALISIS.md. */
class AplicacionSincronaTest {
    final Memoria m = new Memoria();
    final RepositorioDePedidos repo = new RepositorioDePedidos() {
        public Optional<Pedido> buscar(String id) { return Optional.ofNullable(m.pedidos.get(id)).map(p -> Pedido.existente(p.id(), p.cliente(), p.lineas(), p.estado(), p.creadoEn())); }
        public void guardar(Pedido p, EstadoDePedido leido) { m.guardarPedido(p, leido); }
    };
    final Inventario inventario = new Inventario() {
        public Optional<Existencias> existenciasDe(String p) { return Optional.ofNullable(m.stock.get(p)).map(n -> Existencias.de(p, n)); }
        public void guardar(Existencias e) { m.stock.put(e.producto(), e.unidades()); }
    };
    final UnidadDeTrabajo unidad = new UnidadDeTrabajo() {
        public <T> T ejecutar(Supplier<T> trabajo) {
            Map<String, Object> antes = m.instantanea();
            try { return trabajo.get(); } catch (RuntimeException e) { m.revertir(antes); throw e; }
        }
    };
    ServicioDePedidos servicio;

    @BeforeEach void datos() {
        m.pedidos.put("p1", Datos.pedido("p1", EstadoDePedido.PENDIENTE, "a", "b"));
        m.stock.put("a", 5); m.stock.put("b", 5);
        servicio = new ServicioDePedidos(repo, inventario, unidad);
    }

    @Test void casoFeliz() {
        assertEquals(EstadoDePedido.CONFIRMADO, servicio.confirmar("p1").estado());
        assertEquals(EstadoDePedido.CONFIRMADO, m.pedidos.get("p1").estado());
        assertEquals(3, m.stock.get("a")); assertEquals(3, m.stock.get("b"));
    }
    @Test void pedidoInexistente() { assertThrows(PedidoNoEncontrado.class, () -> servicio.confirmar("nada")); }
    @Test void sinExistenciasRevierteTodo() {
        m.stock.put("b", 1);
        assertThrows(ReglaDeNegocioViolada.class, () -> servicio.confirmar("p1"));
        assertEquals(5, m.stock.get("a")); assertEquals(EstadoDePedido.PENDIENTE, m.pedidos.get("p1").estado());
    }
    @Test void yaConfirmadoLoDecideElDominio() {
        servicio.confirmar("p1");
        ReglaDeNegocioViolada e = assertThrows(ReglaDeNegocioViolada.class, () -> servicio.confirmar("p1"));
        assertTrue(e.getMessage().contains("CONFIRMADO"));
        assertEquals(3, m.stock.get("a"), "no se descontó dos veces");
    }
    @Test void unConflictoSeReintenta() {
        m.conflictosPorProvocar = 1;
        assertEquals(EstadoDePedido.CONFIRMADO, servicio.confirmar("p1").estado());
        assertEquals(3, m.stock.get("a"), "el intento fallido se revirtió y el segundo descontó una sola vez");
    }
    @Test void tresConflictosSeRinden() {
        m.conflictosPorProvocar = 3;
        assertThrows(ConflictoDeConcurrencia.class, () -> servicio.confirmar("p1"));
        assertEquals(5, m.stock.get("a"));
    }
}
