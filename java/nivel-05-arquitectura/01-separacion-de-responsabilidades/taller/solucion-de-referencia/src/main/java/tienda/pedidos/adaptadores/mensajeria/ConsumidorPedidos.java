package tienda.pedidos.adaptadores.mensajeria;

import org.springframework.stereotype.Component;
import tienda.pedidos.aplicacion.CrearPedido;
import tienda.pedidos.aplicacion.CrearPedidoComando;
import tienda.pedidos.dominio.Pedido;

/** Segundo canal (mensajería simulada): reutiliza el caso de uso sin tocar el dominio. */
@Component
public class ConsumidorPedidos {

    private final CrearPedido crearPedido;

    ConsumidorPedidos(CrearPedido crearPedido) {
        this.crearPedido = crearPedido;
    }

    /** Mensaje con el formato "cliente;cantidad". */
    public Pedido alRecibir(String mensaje) {
        String[] partes = mensaje.split(";");
        return crearPedido.ejecutar(new CrearPedidoComando(partes[0], Integer.parseInt(partes[1])));
    }
}
