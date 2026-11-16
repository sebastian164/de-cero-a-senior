package tienda.pedidos.aplicacion;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tienda.pedidos.dominio.Pedido;

/** Aplicación: orquesta el caso de uso. No sabe de HTTP ni de SQL. */
@Service
public class CrearPedido {

    private final RepositorioPedidos repositorio;

    public CrearPedido(RepositorioPedidos repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional
    public Pedido ejecutar(CrearPedidoComando comando) {
        Pedido pedido = Pedido.nuevo(comando.cliente(), comando.cantidad());
        return repositorio.guardar(pedido);
    }
}
