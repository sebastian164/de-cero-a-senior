package inventario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import inventario.aplicacion.Puertos.EventosProcesados;
import inventario.aplicacion.Puertos.Existencias;
import inventario.aplicacion.Puertos.PublicadorDeResultados;
import inventario.aplicacion.Puertos.Transaccion;
import inventario.aplicacion.ReservarInventario;
import inventario.dominio.PedidoConfirmado;
import inventario.dominio.PedidoConfirmado.Linea;
import inventario.dominio.ResultadoDeReserva;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** Paso 2: el caso de uso se prueba sin Spring, sin Kafka y sin base de datos (milisegundos). */
class ReservarInventarioTest {

    static final class Memoria implements Existencias, EventosProcesados, PublicadorDeResultados, Transaccion {
        final Map<String, Integer> stock = new HashMap<>(Map.of("a", 10, "b", 3));
        final Map<String, ResultadoDeReserva> procesados = new HashMap<>();
        final List<String> publicados = new ArrayList<>();
        @Override public boolean reservar(String pedidoId, List<Linea> lineas) {
            if (lineas.stream().anyMatch(l -> stock.getOrDefault(l.productoId(), 0) < l.cantidad())) return false;
            lineas.forEach(l -> stock.merge(l.productoId(), -l.cantidad(), Integer::sum));
            return true;
        }
        @Override public boolean registrarSiEsNuevo(String id) { return procesados.putIfAbsent(id, ResultadoDeReserva.INSUFICIENTE) == null; }
        @Override public void guardarResultado(String id, ResultadoDeReserva r) { procesados.put(id, r); }
        @Override public Optional<ResultadoDeReserva> resultadoDe(String id) { return Optional.ofNullable(procesados.get(id)); }
        @Override public void publicar(String eventoId, String pedidoId, ResultadoDeReserva r) { publicados.add(pedidoId + ":" + r); }
        @Override public <T> T ejecutar(Supplier<T> trabajo) { return trabajo.get(); }
    }

    ReservarInventario caso(Memoria m) { return new ReservarInventario(m, m, m, m); }

    @Test
    void reservaCuandoHayDeTodo() {
        Memoria m = new Memoria();
        caso(m).ejecutar(new PedidoConfirmado("e1", "p1", List.of(new Linea("a", 4), new Linea("b", 1))));
        assertEquals(6, m.stock.get("a"));
        assertEquals(2, m.stock.get("b"));
        assertEquals(List.of("p1:RESERVADO"), m.publicados);
    }

    @Test
    void siFaltaAlgoNoDescuentaNada() {
        Memoria m = new Memoria();
        caso(m).ejecutar(new PedidoConfirmado("e1", "p1", List.of(new Linea("a", 4), new Linea("b", 9))));
        assertEquals(10, m.stock.get("a"));
        assertEquals(List.of("p1:INSUFICIENTE"), m.publicados);
    }

    @Test
    void elMismoEventoTresVecesDescuentaUnaVezYRepiteElResultado() {
        Memoria m = new Memoria();
        PedidoConfirmado e = new PedidoConfirmado("e1", "p1", List.of(new Linea("a", 4)));
        for (int i = 0; i < 3; i++) caso(m).ejecutar(e);
        assertEquals(6, m.stock.get("a"));
        assertEquals(List.of("p1:RESERVADO", "p1:RESERVADO", "p1:RESERVADO"), m.publicados);
        assertTrue(m.procesados.containsKey("e1"));
    }
}
