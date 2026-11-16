package tienda.adaptadores.salida.memoria;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import tienda.aplicacion.puertos.salida.RepositorioDePedidos;
import tienda.dominio.Pedido;

public final class PedidosEnMemoria implements RepositorioDePedidos {

    private final Map<String, Pedido> pedidos = new LinkedHashMap<>();
    private final AtomicInteger secuencia = new AtomicInteger();

    @Override
    public String siguienteId() {
        return "pedido-" + secuencia.incrementAndGet();
    }

    @Override
    public synchronized void guardar(Pedido pedido) {
        pedidos.put(pedido.id(), copia(pedido));
    }

    @Override
    public synchronized Optional<Pedido> buscar(String pedidoId) {
        return Optional.ofNullable(pedidos.get(pedidoId)).map(PedidosEnMemoria::copia);
    }

    @Override
    public synchronized List<Pedido> deCliente(String cliente) {
        return pedidos.values().stream().filter(pedido -> pedido.cliente().equals(cliente)).map(PedidosEnMemoria::copia).toList();
    }

    /** Se guardan y se entregan copias: quien tiene un pedido en la mano no cambia lo guardado sin guardar. */
    static Pedido copia(Pedido pedido) {
        return Pedido.existente(pedido.id(), pedido.cliente(), pedido.lineas(), pedido.estado(), pedido.creadoEn());
    }
}
