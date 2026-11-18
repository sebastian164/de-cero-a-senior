package pedidos;

import java.util.HashMap;
import java.util.Map;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.dominio.*;

/** El estado de los dobles en memoria (pedidos y existencias), con instantánea y reversión como una base real. */
final class Memoria {
    Map<String, Pedido> pedidos = new HashMap<>();
    Map<String, Integer> stock = new HashMap<>();
    int conflictosPorProvocar;

    Map<String, Object> instantanea() {
        Map<String, Object> s = new HashMap<>();
        Map<String, Pedido> copia = new HashMap<>();
        pedidos.forEach((k, p) -> copia.put(k, Pedido.existente(p.id(), p.cliente(), p.lineas(), p.estado(), p.creadoEn())));
        s.put("p", copia); s.put("s", new HashMap<>(stock));
        return s;
    }
    /** Lo que otra transacción ya confirmó: la reversión de ESTA transacción no lo borra. */
    Runnable escrituraAjena = () -> { };
    @SuppressWarnings("unchecked") void revertir(Map<String, Object> s) {
        pedidos = (Map<String, Pedido>) s.get("p"); stock = (Map<String, Integer>) s.get("s");
        escrituraAjena.run(); escrituraAjena = () -> { };
    }

    void guardarPedido(Pedido nuevo, EstadoDePedido leido) {
        if (conflictosPorProvocar > 0) { conflictosPorProvocar--; throw new ConflictoDeConcurrencia("el pedido " + nuevo.id()); }
        if (pedidos.get(nuevo.id()).estado() != leido) throw new ConflictoDeConcurrencia("el pedido " + nuevo.id());
        pedidos.put(nuevo.id(), nuevo);
    }
}
