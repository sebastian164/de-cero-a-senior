package tienda;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicioPedidos {

    private final PedidoRepository repositorio;
    private final Clock reloj;

    public ServicioPedidos(PedidoRepository repositorio, Clock reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    @Transactional
    public Pedido crear(SolicitudPedido solicitud) {
        return repositorio.save(new Pedido(solicitud.cliente(), solicitud.total(), reloj.instant()));
    }

    @Transactional(readOnly = true)
    public Pedido buscar(long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pedido " + id));
    }

    @Transactional
    public Pedido entregar(long id) {
        Pedido pedido = buscar(id);
        pedido.entregar();
        return pedido;
    }
}
