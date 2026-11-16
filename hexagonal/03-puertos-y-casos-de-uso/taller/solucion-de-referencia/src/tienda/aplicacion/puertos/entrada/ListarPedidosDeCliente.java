package tienda.aplicacion.puertos.entrada;

import java.util.List;
import tienda.aplicacion.VistaDePedido;

/** El caso de uso que no estaba previsto (paso 8). */
public interface ListarPedidosDeCliente {

    List<VistaDePedido> deCliente(String cliente);
}
