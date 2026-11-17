package taller;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Paso 7, el arreglo: la lectura en una transacción de solo lectura que devuelve un objeto plano, no la entidad. */
@Service
public class VistaDePedido {

    public record Resumen(long id, String estado, List<String> productos) {
    }

    private final Pedidos pedidos;

    public VistaDePedido(Pedidos pedidos) {
        this.pedidos = pedidos;
    }

    @Transactional(readOnly = true)
    public Resumen resumen(long id) {
        Pedido pedido = pedidos.conLineas(id).orElseThrow();
        return new Resumen(pedido.getId(), pedido.getEstado(), pedido.getLineas().stream().map(Linea::getProducto).toList());
    }
}
