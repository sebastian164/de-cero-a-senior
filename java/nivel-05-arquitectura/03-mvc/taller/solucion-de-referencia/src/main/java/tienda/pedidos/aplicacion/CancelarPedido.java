package tienda.pedidos.aplicacion;

import org.springframework.stereotype.Service;
import tienda.pedidos.dominio.Pedido;

import java.time.Clock;
import java.time.LocalDateTime;

/** Caso de uso: compartido por todos los canales. */
@Service
public class CancelarPedido {

    private final RepositorioPedidos repositorio;
    private final Notificador correo;
    private final Clock reloj;

    public CancelarPedido(RepositorioPedidos repositorio, Notificador correo, Clock reloj) {
        this.repositorio = repositorio;
        this.correo = correo;
        this.reloj = reloj;
    }

    public void ejecutar(long id) {
        Pedido pedido = repositorio.buscar(id).orElseThrow(() -> new PedidoNoEncontrado(id));
        pedido.cancelar(LocalDateTime.now(reloj));
        repositorio.guardar(pedido);
        correo.enviar(pedido.cliente(), "Pedido cancelado");
    }
}
