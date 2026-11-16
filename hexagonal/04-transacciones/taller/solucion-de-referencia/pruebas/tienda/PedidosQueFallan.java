package tienda;

import java.util.List;
import java.util.Optional;
import tienda.aplicacion.puertos.salida.RepositorioDePedidos;
import tienda.dominio.Pedido;

/** Envuelve un repositorio real y, mientras esté averiado, falla al guardar: una caída entre descontar y guardar. */
public final class PedidosQueFallan implements RepositorioDePedidos {

    private final RepositorioDePedidos real;
    private boolean averiado;

    public PedidosQueFallan(RepositorioDePedidos real) {
        this.real = real;
    }

    public void averiar() {
        averiado = true;
    }

    public void reparar() {
        averiado = false;
    }

    @Override
    public String siguienteId() {
        return real.siguienteId();
    }

    @Override
    public void guardar(Pedido pedido) {
        if (averiado) {
            throw new IllegalStateException("se perdió la conexión al guardar " + pedido.id());
        }
        real.guardar(pedido);
    }

    @Override
    public Optional<Pedido> buscar(String pedidoId) {
        return real.buscar(pedidoId);
    }

    @Override
    public List<Pedido> deCliente(String cliente) {
        return real.deCliente(cliente);
    }
}
