package transacciones;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Service
public class ImportadorCorregido {
    private final GuardadoTransaccional guardado;
    private final TransactionTemplate tx;
    private final Pedidos pedidos;

    public ImportadorCorregido(GuardadoTransaccional guardado, TransactionTemplate tx, Pedidos pedidos) {
        this.guardado = guardado; this.tx = tx; this.pedidos = pedidos;
    }

    /** Corrección 1: cada pedido en su transacción a través de otro bean. */
    public void importarConOtroBean(List<PedidoDto> lote) {
        for (PedidoDto dto : lote) guardado.guardar(dto);
    }

    /** Corrección 2: transacción explícita con TransactionTemplate. */
    public void importarConTemplate(List<PedidoDto> lote) {
        for (PedidoDto dto : lote) {
            tx.executeWithoutResult(estado -> {
                pedidos.insertarYValidar(new PedidoDto(dto.cliente() + "-cabecera", 0));
                pedidos.insertarYValidar(dto);
            });
        }
    }
}
