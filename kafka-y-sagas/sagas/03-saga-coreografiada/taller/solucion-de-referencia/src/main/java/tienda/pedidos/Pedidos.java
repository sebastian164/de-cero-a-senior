package tienda.pedidos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tienda.bus.Bus;
import tienda.bus.Evento;

/**
 * Pedidos: inicia la compra y decide su resultado final mirando los hechos de los demás.
 * Es también el dueño de la vigilancia de compras atascadas (paso 6): el resultado del pedido es suyo.
 */
public final class Pedidos {

    private final Bus bus;
    public final Map<String, String> estados = new LinkedHashMap<>();
    private final Map<String, Long> creadoEn = new LinkedHashMap<>();

    public Pedidos(Bus bus, boolean cobrarPrimero) {
        this.bus = bus;
        bus.suscribir("EnvioProgramado", e -> cerrar(e, "CONFIRMADO", "PedidoConfirmado"));
        bus.suscribir("InventarioLiberado", e -> cerrar(e, "CANCELADO", "PedidoCancelado"));
        if (cobrarPrimero) {
            // Paso 5: con el cobro primero, la saga termina por otros eventos
            bus.suscribir("PagoRechazado", e -> cerrar(e, "CANCELADO", "PedidoCancelado"));
            bus.suscribir("PagoReembolsado", e -> cerrar(e, "CANCELADO", "PedidoCancelado"));
        } else {
            bus.suscribir("InventarioAgotado", e -> cerrar(e, "CANCELADO", "PedidoCancelado"));
        }
    }

    public void crear(String pedido, long ciclo) {
        estados.put(pedido, "PENDIENTE");
        creadoEn.put(pedido, ciclo);
        bus.publicar("PedidoCreado", pedido);
    }

    private void cerrar(Evento e, String estado, String evento) {
        if (!"PENDIENTE".equals(estados.get(e.correlacion()))) {
            return;                                   // ya cerrado: idempotente ante repeticiones
        }
        estados.put(e.correlacion(), estado);
        bus.publicar(evento, e);
    }

    /** Paso 6: pedidos que siguen PENDIENTE más de "ciclos". Sin orquestador, lo vigila el dueño del resultado. */
    public List<String> atascados(long cicloActual, long ciclos) {
        return estados.entrySet().stream()
                .filter(p -> p.getValue().equals("PENDIENTE") && cicloActual - creadoEn.get(p.getKey()) > ciclos)
                .map(Map.Entry::getKey)
                .toList();
    }
}
