package transacciones;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Corrección 1: guardar vive en otro bean, así la llamada cruza el proxy. */
@Service
public class GuardadoTransaccional {
    private final Pedidos pedidos;

    public GuardadoTransaccional(Pedidos pedidos) { this.pedidos = pedidos; }

    @Transactional
    public void guardar(PedidoDto dto) {
        pedidos.insertarYValidar(new PedidoDto(dto.cliente() + "-cabecera", 0));
        pedidos.insertarYValidar(dto);
    }
}
