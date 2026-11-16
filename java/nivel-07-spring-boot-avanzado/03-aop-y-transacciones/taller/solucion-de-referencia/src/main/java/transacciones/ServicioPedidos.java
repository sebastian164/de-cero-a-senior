package transacciones;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** El ejemplo del README: importar llama a guardar desde adentro. */
@Service
public class ServicioPedidos {
    private final Pedidos pedidos;

    public ServicioPedidos(Pedidos pedidos) { this.pedidos = pedidos; }

    public void importar(List<PedidoDto> lote) {
        for (PedidoDto dto : lote) {
            guardar(dto);              // autoinvocación: @Transactional NO se aplica
        }
    }

    /** Un pedido con dos filas: la intención es que las dos entren o ninguna. */
    @Transactional
    public void guardar(PedidoDto dto) {
        pedidos.insertarYValidar(new PedidoDto(dto.cliente() + "-cabecera", 0));
        pedidos.insertarYValidar(dto);
    }

    @Transactional
    void guardarDePaquete(PedidoDto dto) {                 // método no público, llamado a través del proxy
        pedidos.insertarYValidar(new PedidoDto(dto.cliente() + "-cabecera", 0));
        pedidos.insertarYValidar(dto);
    }
}
