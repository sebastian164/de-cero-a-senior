package pedidos.aplicacion.sincrona;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Supplier;
import pedidos.aplicacion.sincrona.puertos.*;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.aplicacion.PedidoNoEncontrado;
import tienda.dominio.EstadoDePedido;
import tienda.dominio.Existencias;
import tienda.dominio.Pedido;
import tienda.dominio.Reserva;

public class ServicioDePedidos {
    public static final int INTENTOS = 3;
    private final RepositorioDePedidos pedidos; private final Inventario inventario; private final UnidadDeTrabajo unidad;

    public ServicioDePedidos(RepositorioDePedidos pedidos, Inventario inventario, UnidadDeTrabajo unidad) {
        this.pedidos = pedidos; this.inventario = inventario; this.unidad = unidad;
    }

    public Pedido consultar(String id) { return pedidos.buscar(id).orElseThrow(() -> new PedidoNoEncontrado(id)); }

    /** Confirma y reserva las existencias, todo o nada. Las reglas las decide el dominio; aquí solo se coordina. */
    public Pedido confirmar(String id) {
        return conReintento(() -> unidad.ejecutar(() -> {
            Pedido pedido = consultar(id);
            EstadoDePedido leido = pedido.estado();
            pedido.confirmar();                                           // el dominio decide si se puede
            Map<String, Existencias> existencias = existenciasDe(pedido);
            Reserva.reservar(pedido, existencias);                        // y también si alcanza
            existencias.values().forEach(inventario::guardar);
            pedidos.guardar(pedido, leido);
            return pedido;
        }));
    }

    /** En orden de producto: todas las transacciones toman los bloqueos en el mismo orden y no se abrazan mortalmente. */
    private Map<String, Existencias> existenciasDe(Pedido pedido) {
        Map<String, Existencias> existencias = new LinkedHashMap<>();
        for (String producto : new TreeSet<>(pedido.unidadesPorProducto().keySet()))
            existencias.put(producto, inventario.existenciasDe(producto).orElseGet(() -> Existencias.de(producto, 0)));
        return existencias;
    }

    private <T> T conReintento(Supplier<T> operacion) {
        for (int intento = 1; ; intento++) {
            try { return operacion.get(); }
            catch (ConflictoDeConcurrencia c) { if (intento == INTENTOS) throw c; }
        }
    }
}
