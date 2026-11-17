package taller;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** "Confirmar pedido" de dos formas: el paso 1 (todo en una transacción) y el paso 5 (transacciones cortas). */
@Service
public class ConfirmarPedido {

    private final Pedidos pedidos;
    private final ServicioDePagos pagos;
    private final TransactionTemplate transaccion;

    public ConfirmarPedido(Pedidos pedidos, ServicioDePagos pagos, PlatformTransactionManager transacciones) {
        this.pedidos = pedidos;
        this.pagos = pagos;
        this.transaccion = new TransactionTemplate(transacciones);
    }

    /** Paso 1: @Transactional alrededor de todo. La conexión está tomada mientras pagos responde. */
    @Transactional
    public String todoEnUnaTransaccion(long id) {
        Pedido pedido = pedidos.findById(id).orElseThrow();
        pagos.cobrar(id);                                        // con la conexión en la mano
        pedido.confirmar();
        pedidos.save(pedido);
        return pedido.getEstado();
    }

    /** Paso 5: leer en una transacción corta, llamar fuera, guardar en otra transacción corta. */
    public String transaccionesCortas(long id) {
        transaccion.execute(estado -> pedidos.findById(id).orElseThrow().getTotal());      // lee y devuelve la conexión
        pagos.cobrar(id);                                        // sin conexión
        return transaccion.execute(estado -> {
            Pedido pedido = pedidos.findById(id).orElseThrow();
            pedido.confirmar();
            pedidos.save(pedido);
            return pedido.getEstado();
        });
    }
}
